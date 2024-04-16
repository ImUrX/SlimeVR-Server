import { useIsTauri } from '@/hooks/breakpoint';
import { info as tInfo, error as tError, warn as tWarn } from '@tauri-apps/plugin-log';

export function info(msg: string) {
  if (useIsTauri()) {
    tInfo(msg);
  } else {
    console.log(msg);
  }
}

export function error(msg: string) {
  if (useIsTauri()) {
    tError(msg);
  } else {
    console.error(msg);
  }
}

export function warn(msg: string) {
  if (useIsTauri()) {
    tWarn(msg);
  } else {
    console.warn(msg);
  }
}
