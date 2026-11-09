const { withAndroidStyles, withDangerousMod, withMainActivity } = require('expo/config-plugins');
const fs = require('fs'); const path = require('path');
function writeResources(res) {
 const files = {
  'drawable/ic_launcher_background.xml': '<shape xmlns:android="http://schemas.android.com/apk/res/android"><solid android:color="@color/iconBackground"/></shape>',
  'values/pomodoro_launch.xml': '<resources><color name="pomodoro_launch_background">#F2F2F7</color></resources>',
  'values-night/pomodoro_launch.xml': '<resources><color name="pomodoro_launch_background">#000000</color></resources>',
  'drawable/launch_empty.xml': '<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="1dp" android:height="1dp" android:viewportWidth="1" android:viewportHeight="1"><path android:fillColor="#00000000" android:pathData="M0,0h1v1h-1z"/></vector>',
  'values-v31/pomodoro_launch.xml': '<resources><style name="Theme.Pomodoro.LaunchLight" parent="Theme.App.SplashScreen"><item name="android:windowBackground">#F2F2F7</item><item name="android:windowSplashScreenBackground">#F2F2F7</item></style><style name="Theme.Pomodoro.LaunchDark" parent="Theme.App.SplashScreen"><item name="android:windowBackground">#000000</item><item name="android:windowSplashScreenBackground">#000000</item></style><style name="Theme.App.SplashScreen" parent="AppTheme"><item name="android:windowBackground">@color/pomodoro_launch_background</item><item name="android:windowSplashScreenBackground">@color/pomodoro_launch_background</item><item name="android:windowSplashScreenAnimatedIcon">@drawable/launch_empty</item><item name="android:windowSplashScreenAnimationDuration">0</item><item name="android:windowDisablePreview">true</item></style></resources>'
 };
 for (const [file, body] of Object.entries(files)) { const target = path.join(res, file); fs.mkdirSync(path.dirname(target), { recursive: true }); fs.writeFileSync(target, body + '\n'); }
 for (const folder of fs.readdirSync(res)) { const old = path.join(res, folder, 'splashscreen_logo.png'); if (fs.existsSync(old)) fs.unlinkSync(old); }
}
module.exports = config => {
 config = withMainActivity(config, c => { const needle = '    setTheme(R.style.AppTheme);'; const addition = `    val launchPrefs = getSharedPreferences("pomodoro-appearance", 0)
    val launchTheme = launchPrefs.getString("theme", "system")
    val launchDark = launchTheme == "dark" || (launchTheme == "system" && (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES)
    window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(if (launchDark) android.graphics.Color.BLACK else android.graphics.Color.rgb(242, 242, 247)))`; if (!c.modResults.contents.includes('pomodoro-appearance')) c.modResults.contents = c.modResults.contents.replace(needle, needle + '\n' + addition); return c; });
 config = withAndroidStyles(config, c => { for (const style of c.modResults.resources.style || []) { if (!['AppTheme', 'Theme.App.SplashScreen'].includes(style.$.name)) continue; style.item ||= []; style.item = style.item.filter(item => !['android:windowBackground', 'android:windowDisablePreview'].includes(item.$.name)); style.item.push({ $: { name: 'android:windowBackground' }, _: '@color/pomodoro_launch_background' }); if (style.$.name === 'Theme.App.SplashScreen') style.item.push({ $: { name: 'android:windowDisablePreview' }, _: 'true' }); } return c; });
 return withDangerousMod(config, ['android', async c => { writeResources(path.join(c.modRequest.platformProjectRoot, 'app/src/main/res')); return c; }]);
};
module.exports.writeResources = writeResources;
