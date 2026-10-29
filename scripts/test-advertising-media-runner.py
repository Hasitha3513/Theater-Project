"""Offline owner-resource/cleanup boundary checks. Never contacts Docker or a provider."""
from pathlib import Path
import importlib.util,json,subprocess,tempfile,unittest
from unittest.mock import patch
spec=importlib.util.spec_from_file_location('ad_media_runner',Path(__file__).with_name('verify-advertising-media-a.py'));m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
class ResourceBoundaryTests(unittest.TestCase):
 def fixture(self,root):
  run='a'*32;path=Path(root)/('cn-ads-a-'+run);path.mkdir();return path,{'run':run,'name':path.name,'task':m.TASK,'image':m.IMAGE}
 def test_exact_manifest(self):
  with tempfile.TemporaryDirectory() as d:
   p,r=self.fixture(d);self.assertEqual(m.owned_manifest(r,p),p.name)
 def test_foreign_run_and_image_rejected(self):
  with tempfile.TemporaryDirectory() as d:
   p,r=self.fixture(d)
   for key,value in [('run','b'*32),('task','other-task'),('image','unpinned'),('name','other')]:
    bad=dict(r);bad[key]=value
    with self.subTest(key=key),self.assertRaisesRegex(RuntimeError,'OWNERSHIP_MISMATCH'):m.owned_manifest(bad,p)
 def test_label_mismatch_rejected(self):
  r={'run':'a'*32};good={'Labels':{'com.cinenest.task':m.TASK,'com.cinenest.run':r['run']}};m.owned_labels(good,r)
  for info in [{'Labels':{}},{'Config':{'Labels':{'com.cinenest.task':m.TASK,'com.cinenest.run':'b'*32}}}]:
   with self.assertRaisesRegex(RuntimeError,'OWNERSHIP_MISMATCH'):m.owned_labels(info,r)
 def test_small_data_measurable(self):
  with tempfile.TemporaryDirectory() as d:
   p=Path(d);(p/'synthetic').write_bytes(b'synthetic');self.assertLessEqual(m.measure(p),m.MAX_DATA)
 def test_symlink_never_followed(self):
  with tempfile.TemporaryDirectory() as d:
   p=Path(d);(p/'foreign').symlink_to('/etc')
   with self.assertRaisesRegex(RuntimeError,'BUDGET_UNMEASURABLE'):m.measure(p)
 def test_over_budget_denied(self):
  with tempfile.TemporaryDirectory() as d,patch.object(m.subprocess,'run',return_value=subprocess.CompletedProcess([],0,stdout=str(m.MAX_DATA+1)+' path\n')):
   with self.assertRaisesRegex(RuntimeError,'MONITORED_BUDGET_EXCEEDED'):m.measure(Path(d))
 def test_external_tmpfs_and_log_reserve_counted(self):
  size=m.MAX_DATA-m.EXTERNAL_DATA_RESERVE+1
  values=[subprocess.CompletedProcess([],0,stdout=str(size)+" path\n"),subprocess.CompletedProcess([],0,stdout=str((size+1023)//1024)+" path\n")]
  with tempfile.TemporaryDirectory() as d,patch.object(m.subprocess,"run",side_effect=values):
   with self.assertRaisesRegex(RuntimeError,"MONITORED_BUDGET_EXCEEDED"):m.measure(Path(d))
 def test_unmeasurable_command_denied(self):
  with tempfile.TemporaryDirectory() as d,patch.object(m.subprocess,'run',side_effect=subprocess.CalledProcessError(1,['du'])):
   with self.assertRaises(subprocess.CalledProcessError):m.measure(Path(d))
 def test_no_filesystem_quota_claim(self):
  source=Path(m.__file__).read_text();self.assertIn('MONITORED_NOT_KERNEL_FILESYSTEM_QUOTA',source);self.assertNotIn('volume prune',source);self.assertNotIn('system prune',source)
if __name__=='__main__':unittest.main()
