"""Assemble native settings classes separately on case-insensitive filesystems."""
from pathlib import Path
import os
import subprocess
from . import dexpatch

def apply(results, tools, workspace, api):
    work = Path(workspace) / 'settings-row'
    work.mkdir(parents=True, exist_ok=True)
    selected = {}
    for name, (data, counts) in results.items():
        if data is None:
            continue
        for descriptor in (dexpatch.SETTINGS_ROWS, dexpatch.SETTINGS_GROUP):
            if dexpatch.defines(data, descriptor):
                path = work / name
                path.write_bytes(data)
                selected[name] = (path, descriptor)
                subprocess.run(['java', '-Xmx768m', '-cp', tools.smali,
                    'com.android.tools.smali.baksmali.Main', 'd', '--classes', descriptor,
                    '-o', str(work / 'smali'), str(path)], check=True)
    counts = dexpatch.rewrite_settings_row(str(work / 'smali'))
    if counts.get(dexpatch.SETTINGS_LABEL) != 2:
        raise RuntimeError('Could not connect both native settings-row hooks')
    # Change only our copied row, never the shared music icon or other rows.
    row_file = work / 'smali' / 'X' / '0MUp.smali'
    text = row_file.read_text(encoding='utf-8')
    start = text.index('.method public static final margyt(')
    end = text.index('.end method', start)
    body = text[start:end]
    old = 'sget-object v0, LX/0LMt;->o:LX/0LPz;'
    replacement = ('invoke-static {}, Lapp/blaxyt/mod/SettingsCell;->icon()Ljava/lang/Object;\n\n'
                   '    move-result-object v0\n\n    check-cast v0, LX/0LPz;')
    if old in body:
        body = body.replace(old, replacement, 1)
    elif 'SettingsCell;->icon()' not in body:
        raise RuntimeError('Native settings icon call site changed')
    row_file.write_text(text[:start] + body + text[end:], encoding='utf-8')
    patch = work / 'settings.dex'
    dexpatch.Smali(tools.smali, api, jobs=2, heap='768m').assemble(str(work / 'smali'), str(patch))
    task = Path(__file__).resolve().parents[1]
    helper = work / 'merge-classes'
    helper.mkdir(exist_ok=True)
    cp = os.environ.get('BLAXYT_DEX_HELPER_JAR', '')
    if not cp or not Path(cp).is_file():
        raise RuntimeError('Set BLAXYT_DEX_HELPER_JAR to APKEditor.jar (org.jf.dexlib2 and its dependencies)')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-cp', cp, '-d', str(helper),
                    str(task / 'build_helpers' / 'CanonicalizeDex.java'),
                    str(task / 'build_helpers' / 'MergeSettings.java')], check=True)
    for name, (source, descriptor) in selected.items():
        output = work / ('fixed-' + name)
        subprocess.run(['java', '-Xmx1200m', '-cp', str(helper) + os.pathsep + cp,
                        'MergeSettings', str(source), str(patch), str(output)], check=True)
        data, current = results[name]
        current = dict(current)
        current[dexpatch.SETTINGS_LABEL] = 1
        results[name] = (output.read_bytes(), current)
    print('    Native settings row integrated from isolated class files.', flush=True)
