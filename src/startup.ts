import type { State } from './model';

export function replayStartupChanges(initial: State, changes: Array<(state: State) => State>, device: string): State {
  let local = initial;
  for (const change of changes) {
    const previous = local;
    local = change(local);
    if (previous.timer.status === 'running' && local.timer.status === 'running' &&
        previous.timer.id === local.timer.id && previous.timer.startedAt !== local.timer.startedAt) {
      local = { ...local, timer: { ...local.timer, startedAt: previous.timer.startedAt } };
    }
  }
  if (changes.length && !local.timer.device) local = { ...local, timer: { ...local.timer, device } };
  return local;
}
