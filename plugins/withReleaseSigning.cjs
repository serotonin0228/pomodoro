const { withAppBuildGradle, withGradleProperties } = require('expo/config-plugins');

module.exports = function withReleaseSigning(config) {
  config = withGradleProperties(config, config => {
    config.modResults = config.modResults.filter(item => item.key !== 'android.enableBundleCompression');
    config.modResults.push({ type: 'property', key: 'android.enableBundleCompression', value: 'true' });
    return config;
  });
  return withAppBuildGradle(config, config => {
    const marker = '// pomodoro-release-signing';
    if (!config.modResults.contents.includes(marker)) {
      config.modResults.contents += `
${marker}
// Signing material is supplied at build time and is never committed to the app.
android {
    signingConfigs {
        pomodoroRelease {
            def signingFile = System.getenv('POMODORO_STORE_FILE')
            if (signingFile != null) {
                storeFile file(signingFile)
                storePassword System.getenv('POMODORO_STORE_PASSWORD')
                keyAlias System.getenv('POMODORO_KEY_ALIAS')
                keyPassword System.getenv('POMODORO_KEY_PASSWORD')
            }
        }
    }
    buildTypes {
        release {
            signingConfig signingConfigs.pomodoroRelease
        }
    }
}
`;
    }
    return config;
  });
};
