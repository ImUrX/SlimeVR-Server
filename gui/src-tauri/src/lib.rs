use std::panic;
use std::sync::atomic::AtomicBool;
use std::sync::atomic::Ordering;
use std::sync::Arc;
use std::sync::Mutex;
use std::thread;
use std::time::Duration;
use std::time::Instant;

use clap::Parser;
use color_eyre::Result;
#[cfg(desktop)]
use state::WindowState;
use tauri::{Manager, RunEvent, WindowEvent};
use tauri_plugin_log::Target;
use tauri_plugin_log::TargetKind;
use tauri_plugin_shell::process::CommandChild;

#[cfg(desktop)]
use crate::util::valid_java_paths;
use crate::util::{get_launch_path, show_error, Cli, JAVA_BIN, MINIMUM_JAVA_VERSION};

#[cfg(desktop)]
mod state;
#[cfg(desktop)]
mod tray;
mod util;

#[cfg(desktop)]
#[tauri::command]
#[cfg(desktop)]
fn update_window_state(
	window: tauri::Window,
	state: tauri::State<Mutex<WindowState>>,
) -> Result<(), String> {
	let mut lock = state.lock().unwrap();
	lock.update_state(&window, false)
		.map_err(|e| format!("{:?}", e))?;
	if window.is_maximized().map_err(|e| e.to_string())? {
		window.unmaximize().map_err(|e| e.to_string())?;
		lock.update_state(&window, true)
			.map_err(|e| format!("{:?}", e))?;
	}
	Ok(())
}

#[tauri::command]
fn dummy_command() {
	()
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() -> Result<()> {
	log_panics::init();
	let hook = panic::take_hook();
	// Make an error dialog box when panicking
	panic::set_hook(Box::new(move |panic_info| {
		show_error(&panic_info.to_string());
		hook(panic_info);
	}));

	let cli = Cli::parse();
	let tauri_context = tauri::generate_context!();

	// Ensure child processes die when spawned on windows
	// and then check for WebView2's existence
	#[cfg(windows)]
	{
		use crate::util::webview2_exists;
		use win32job::{ExtendedLimitInfo, Job};

		let mut info = ExtendedLimitInfo::new();
		info.limit_kill_on_job_close();
		let job = Job::create_with_limit_info(&mut info).expect("Failed to create Job");
		job.assign_current_process()
			.expect("Failed to assign current process to Job");

		// We don't do anything with the job anymore, but we shouldn't drop it because that would
		// terminate our process tree. So we intentionally leak it instead.
		std::mem::forget(job);

		if !webview2_exists() {
			// This makes a dialog appear which let's you press Ok or Cancel
			// If you press Ok it will open the SlimeVR installer documentation
			use rfd::{
				MessageButtons, MessageDialog, MessageDialogResult, MessageLevel,
			};

			let confirm = MessageDialog::new()
				.set_title("SlimeVR")
				.set_description("Couldn't find WebView2 installed. You can install it with the SlimeVR installer")
				.set_buttons(MessageButtons::OkCancel)
				.set_level(MessageLevel::Error)
				.show();
			if confirm == MessageDialogResult::Ok {
				open::that("https://docs.slimevr.dev/server-setup/installing-and-connecting.html#install-the-latest-slimevr-installer").unwrap();
			}
			return Ok(());
		}
	}

	// Spawn server process
	let exit_flag = Arc::new(AtomicBool::new(false));
	let backend = Arc::new(Mutex::new(Option::<CommandChild>::None));
	let backend_termination = backend.clone();
	let run_path = get_launch_path(cli);

	#[cfg(desktop)]
	let server_info = if let Some(p) = run_path {
		log::info!("Server found on path: {}", p.to_str().unwrap());

		// Check if any Java already installed is compatible
		let jre = p.join("jre/bin").join(JAVA_BIN);
		let java_bin = jre
			.exists()
			.then(|| jre.into_os_string())
			.or_else(|| valid_java_paths().first().map(|x| x.0.to_owned()));
		let Some(java_bin) = java_bin else {
			show_error(&format!("Couldn't find a compatible Java version, please download Java {} or higher", MINIMUM_JAVA_VERSION));
			return Ok(());
		};

		log::info!("Using Java binary: {:?}", java_bin);
		Some((java_bin, p))
	} else {
		log::warn!("No server found. We will not start the server.");
		None
	};

	let exit_flag_terminated = exit_flag.clone();
	let build_result = tauri::Builder::default()
		.plugin(tauri_plugin_dialog::init())
		.plugin(tauri_plugin_fs::init())
		.plugin(tauri_plugin_os::init())
		.plugin(tauri_plugin_shell::init())
		.plugin(tauri_plugin_store::Builder::default().build())
		.plugin(
			tauri_plugin_log::Builder::new()
				.targets([
					Target::new(TargetKind::Stdout),
					Target::new(TargetKind::LogDir {
						file_name: Some("tauri".into()),
					}),
					Target::new(TargetKind::Webview),
				])
				.build(),
		)
		.invoke_handler(tauri::generate_handler![
			dummy_command,
			#[cfg(desktop)]
			update_window_state,
			#[cfg(desktop)]
			tray::update_translations,
			#[cfg(desktop)]
			tray::update_tray_text,
		])
		.setup(move |app| {
			#[cfg(desktop)]
			let window_state = WindowState::open_state(app.path().app_config_dir().unwrap())
				.unwrap_or_default();

			let window = tauri::WebviewWindowBuilder::new(
				app,
				"main",
				tauri::WebviewUrl::App("index.html".into()),
			)
			.title("SlimeVR")
			.inner_size(1289.0, 709.0)
			.min_inner_size(393.0, 667.0)
			.resizable(true)
			.visible(true)
			.decorations(false)
			.fullscreen(false)
			.build()?;

			#[cfg(desktop)]
			{
				if window_state.is_old() {
					window_state.update_window(&window.as_ref().window(), false)?;
				}

				let handle = app.handle();
				tray::create_tray(&handle)?;

				app.manage(Mutex::new(window_state));
			}

			#[cfg(desktop)]
			if let Some((java_bin, p)) = server_info {
				let app_handle = app.app_handle().clone();
				tauri::async_runtime::spawn(async move {
					use tauri_plugin_shell::{process::CommandEvent, ShellExt};

					let (mut rx, child) = app_handle
						.shell()
						.command(java_bin.to_str().unwrap())
						.current_dir(p)
						.args(["-Xmx512M", "-jar", "slimevr.jar", "run"])
						.spawn()
						.expect("Unable to start the server jar");

					{
						let mut lock = backend.lock().unwrap();
						*lock = Some(child)
					}

					while let Some(cmd_event) = rx.recv().await {
						let emit_me = match cmd_event {
							CommandEvent::Stderr(v) => {
								("stderr", String::from_utf8(v).unwrap_or_default())
							}
							CommandEvent::Stdout(v) => {
								("stdout", String::from_utf8(v).unwrap_or_default())
							}
							CommandEvent::Error(s) => ("error", s),
							CommandEvent::Terminated(s) => {
								exit_flag_terminated.store(true, Ordering::Relaxed);
								("terminated", format!("{s:?}"))
							}
							_ => ("other", "".to_string()),
						};
						app_handle
							.emit("server-status", emit_me)
							.expect("Check server log files. \nFailed to emit");
					}
					log::error!("Java server receiver died");
					app_handle
						.emit("server-status", ("other", "receiver cancelled"))
						.expect("Failed to emit");
				});
			}
			Ok(())
		})
		.on_window_event(|w, e| match e {
			#[cfg(desktop)]
			WindowEvent::CloseRequested { .. } => {
				let window_state = w.state::<Mutex<WindowState>>();
				if let Err(e) = update_window_state(w.clone(), window_state) {
					log::error!("failed to update window state {}", e)
				}
			}
			// See https://github.com/tauri-apps/tauri/issues/4012#issuecomment-1449499149
			#[cfg(windows)]
			WindowEvent::Resized(_) => std::thread::sleep(std::time::Duration::from_nanos(1)),
			_ => (),
		})
		.build(tauri_context);
	match build_result {
		Ok(app) => {
			app.run(move |app_handle, event| match event {
				#[cfg(desktop)]
				RunEvent::ExitRequested { .. } => {
					let window_state = app_handle.state::<Mutex<WindowState>>();
					let lock = window_state.lock().unwrap();
					let config_dir = app_handle.path().app_config_dir().unwrap();
					let window_state_res = lock.save_state(config_dir);
					match window_state_res {
						Ok(()) => log::info!("saved window state"),
						Err(e) => log::error!("failed to save window state: {}", e),
					}

					let mut lock = backend_termination.lock().unwrap();
					let Some(ref mut child) = *lock else { return };
					let write_result = child.write(b"exit\n");
					match write_result {
						Ok(()) => log::info!("send exit to backend"),
						Err(_) => log::error!("fail to send exit to backend"),
					}
					let ten_seconds = Duration::from_secs(10);
					let start_time = Instant::now();
					while start_time.elapsed() < ten_seconds {
						if exit_flag.load(Ordering::Relaxed) {
							break;
						}
						thread::sleep(Duration::from_secs(1));
					}
				}
				_ => {}
			});
		}
		#[cfg(windows)]
		// Often triggered when the user doesn't have webview2 installed
		Err(tauri::Error::Runtime(tauri_runtime::Error::CreateWebview(error))) => {
			// I should log this anyways, don't want to dig a grave by not logging the error.
			log::error!("CreateWebview error {}", error);

			use rfd::{
				MessageButtons, MessageDialog, MessageDialogResult, MessageLevel,
			};

			let confirm = MessageDialog::new()
				.set_title("SlimeVR")
				.set_description("You seem to have a faulty installation of WebView2. You can check a guide on how to fix that in the docs!")
				.set_buttons(MessageButtons::OkCancel)
				.set_level(MessageLevel::Error)
				.show();
			if confirm == MessageDialogResult::Ok {
				open::that("https://docs.slimevr.dev/common-issues.html#webview2-is-missing--slimevr-gui-crashes-immediately--panicked-at--webview2error").unwrap();
			}
		}
		Err(error) => {
			log::error!("tauri build error {}", error);
			show_error(&error.to_string());
		}
	}

	Ok(())
}
