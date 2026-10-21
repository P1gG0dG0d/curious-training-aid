"""Assemble a submission from an explicit source allowlist; never package local SDKs or credentials."""
from pathlib import Path
import shutil
import zipfile
import hashlib

ROOT = Path(__file__).resolve().parents[1]
STAGE = ROOT / '提交包'
SOURCE = STAGE / '源码'
NAME = '校园生活+我爱练腿队+好奇训练'

def main():
    SOURCE.mkdir(parents=True, exist_ok=True)
    # Only the known generated source copy is replaced; the working project is untouched.
    assert SOURCE.resolve() == ROOT.resolve() / '提交包' / '源码'
    shutil.rmtree(SOURCE)
    SOURCE.mkdir()
    for directory in ('app/src', 'app/schemas', 'gradle', 'licenses'):
        shutil.copytree(ROOT / directory, SOURCE / directory)
    for filename in ('app/build.gradle.kts', 'build.gradle.kts', 'settings.gradle.kts',
                     'gradle.properties', 'gradlew', 'gradlew.bat', '.gitignore',
                     'THIRD-PARTY-LICENSES.md', '微信图片_20261018163238_274_46.png'):
        shutil.copy2(ROOT / filename, SOURCE / filename)
    (SOURCE / 'scripts').mkdir()
    shutil.copy2(ROOT / 'scripts/generate-launcher-icon.py', SOURCE / 'scripts/generate-launcher-icon.py')
    readme = (STAGE / 'README.md').read_text(encoding='utf-8')
    readme = readme.replace('](允许后续开源.md)', '](../允许后续开源.md)')
    (SOURCE / 'README.md').write_text(readme, encoding='utf-8')
    (STAGE / '安装包').mkdir(exist_ok=True)
    shutil.copy2(ROOT / 'dist/haoqi-training-0.2.11.apk', STAGE / '安装包/好奇训练-0.2.11.apk')
    files = sorted(p for p in STAGE.rglob('*') if p.is_file())
    # Block accidental build products, local config, keys and caches in the source copy.
    for p in files:
        rel = p.relative_to(STAGE)
        assert not any(s in rel.parts for s in ('.git', '.gradle', '.kotlin', 'build', '.android-test', '__pycache__')), rel
        assert p.name != 'local.properties' and p.suffix not in ('.jks', '.keystore'), rel
    target = ROOT / 'dist' / (NAME + '.zip')
    with zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as archive:
        for p in files:
            archive.write(p, (Path(NAME) / p.relative_to(STAGE)).as_posix())
    with zipfile.ZipFile(target) as archive:
        assert archive.testzip() is None
    print(f'{target}\nfiles={len(files)} bytes={target.stat().st_size}')
    print('SHA256=' + hashlib.sha256(target.read_bytes()).hexdigest())

if __name__ == '__main__':
    main()
