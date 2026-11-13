"""Run the actual Android guard policy on the JVM with the cached Gradle Kotlin compiler."""
from pathlib import Path
import os
import subprocess
import tempfile
cache = Path(os.environ.get('GRADLE_USER_HOME', '/workspace/.gradle')) / 'caches/modules-2/files-2.1'
def jar(group, artifact, version):
    return next((cache / group / artifact / version).glob('*/*.jar'))
version = '2.2.21'
stdlib = jar('org.jetbrains.kotlin', 'kotlin-stdlib', version)
cp = [jar('org.jetbrains.kotlin', name, version) for name in ['kotlin-compiler-embeddable', 'kotlin-script-runtime', 'kotlin-daemon-embeddable']]
cp += [next((cache / 'org.jetbrains.kotlin/kotlin-reflect').glob('*/*/*.jar')), stdlib, jar('org.jetbrains', 'annotations', '13.0'), jar('org.jetbrains.kotlinx', 'kotlinx-coroutines-core-jvm', '1.8.0')]
java = str(Path(os.environ.get('JAVA_HOME', '/workspace/jdk21')) / 'bin/java')
with tempfile.TemporaryDirectory(prefix='guard-policy-') as folder:
    subprocess.run([java, '-cp', ':'.join(map(str, cp)), 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect', '-classpath', str(stdlib), '-d', folder, 'plugins/focus-native/FocusGuardPolicy.kt', 'tests/native/FocusGuardPolicyTest.kt'], check=True)
    subprocess.run([java, '-cp', folder + ':' + str(stdlib), 'com.pomodoro.focus.FocusGuardPolicyTestKt'], check=True)
