"""Cached DEX landing sites must remain callable after conversation UI rollback."""
from pathlib import Path
import shutil,subprocess,tempfile,unittest
ROOT=Path(__file__).resolve().parents[1]
class ConversationCompatibility(unittest.TestCase):
 @unittest.skipUnless(shutil.which('javac'), 'JDK required')
 def test_cached_landing_sites_without_ui_dependencies(self):
  with tempfile.TemporaryDirectory() as directory:
   work=Path(directory)
   sources={
    'android/view/View.java':'package android.view;public class View{}',
    'app/blaxyt/mod/ConversationStyle.java':(ROOT/'inject/java/app/blaxyt/mod/ConversationStyle.java').read_text(encoding='utf8'),
    'app/blaxyt/mod/Check.java':'package app.blaxyt.mod;public class Check{public static void main(String[] args){if(ConversationStyle.enabled())throw new AssertionError("design still enabled");for(android.view.View v:new android.view.View[]{null,new android.view.View()}){ConversationStyle.input(v);ConversationStyle.header(v);ConversationStyle.story(v);ConversationStyle.greeting(v);ConversationStyle.profile(null,v);}}}'}
   for name,value in sources.items():
    p=work/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(value,encoding='utf8')
   subprocess.run(['javac','-encoding','UTF-8','-d',str(work),*[str(work/n) for n in sources]],check=True,capture_output=True)
   subprocess.run(['java','-cp',str(work),'app.blaxyt.mod.Check'],check=True,capture_output=True)
if __name__=='__main__':unittest.main()
