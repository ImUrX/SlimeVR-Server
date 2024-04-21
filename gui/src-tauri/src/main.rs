#![cfg_attr(all(not(debug_assertions), windows), windows_subsystem = "windows")]
use color_eyre::Result;

fn main() -> Result<()> {
	slimevr_lib::run()?;
	Ok(())
}
