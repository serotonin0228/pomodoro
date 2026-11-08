// Online devices share a server-corrected epoch; offline devices retain the last correction.
let correction = 0;
export const timeNow = () => Date.now() + correction;
export const setTimeCorrection = (value: number) => { if (Number.isFinite(value)) correction = value; };
