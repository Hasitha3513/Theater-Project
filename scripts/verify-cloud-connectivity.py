"""Bounded non-production Cloud Run diagnostic artifact. Default is offline only.
No GCP command, secret payload read, database connection or deployment at import/plan.
The approved operational controller still requires READY source,exact approval,
local qualification,hosted CI,resource/cost/time gates and attributable cleanup.
"""
import argparse,hashlib,json,os,re,shutil,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
PROJECT='project-f903a5f6-f535-4016-aae'
APPROVAL_SHA256='f0791689aba5c235c9e3e93effb61c85650eff48a8feedae59a36b283798bde8'
JDK='eclipse-temurin:25.0.4.1_1-jdk-alpine@sha256:5122b1df12f028f10c7399bedf1212e1497962a6482af3338eea2ace485f561e'
JRE='eclipse-temurin:25.0.4.1_1-jre-alpine@sha256:b7528962e65d43064d8a8b67ba175367896bc948363ea2d3fcd7f10d34e1f048'
MIGRATION_AGGREGATE_SHA256='776d9fda80ea4a6d78d0de7710d1f92a1732a86b9be4ca84eb3015552ade1617'
DEPENDENCIES={'org/flywaydb/flyway-core/12.4.0/flyway-core-12.4.0.jar': 'c3f93df1f3e0c9d14652adb5ade97401f2ede503859ac693c2af5c06e2797799', 'org/flywaydb/flyway-database-postgresql/12.4.0/flyway-database-postgresql-12.4.0.jar': 'abc075a5c3cbf37dc64b28fec7e2398fb14f31109f7ab6271854738797407b85', 'org/postgresql/postgresql/42.7.13/postgresql-42.7.13.jar': '6e0e4cc2d8cae902084f8a2b18728b073a6fd9d1f87c9d8bff8f298c18185b93', 'tools/jackson/core/jackson-core/3.1.5/jackson-core-3.1.5.jar': '9431b7fa2673bbb618c11d865fe15e13222fd182a214ff998cb7e56afd8f35d2', 'tools/jackson/core/jackson-databind/3.1.5/jackson-databind-3.1.5.jar': '3a2338d996fd3056791df8d335fa9ba8a62a706ed4245ecf81b3e583df37d08a', 'com/fasterxml/jackson/core/jackson-annotations/2.22/jackson-annotations-2.22.jar': '21ddb598807d3a51a876704eb979d9296e1c6a6f47ab1826ff88c6d6a127a2d0'}
SQL_FILES={'00-activation-authority-probe.sql.proposed': 'cd2a892c70da4197cacc61e815419ed7014de0f441606c2cc40b851a78c28c2d', '01-protected-credentials-and-migrator-login.psql.proposed': 'c4530905d672f4a31ebd6eb284ffcc7cfefa3fbb81bf66e39c16c4da8c060b15', '02-post-migration-runtime-grants.sql.proposed': '52c43896824718533d5ecd113d2f1016f3009e8260927278a70edb836ce8eb52', '03-runtime-login-and-migrator-revocation.sql.proposed': '515a05725a35f91887739482a3d3b9c3c38922da2f1d74352a71612a99fc1ae6', '04-login-revocation-and-session-cleanup.sql.proposed': '01602c6134e185cfb86812033b76e5e8fde831e734dfd9af600803dfe50d5ad5', '05-runtime-readonly-verification.sql.proposed': '2030021053c7f24b89e9b0d7fd391e5be0e716a1db25dd7ed30fc112f46aea1c', '06-data-preserving-quarantine.sql.proposed': 'ca93cc636de0544fcab1783e81086d1d77e044feea33ca4195f1ddd28330a676'}
JAVA_SOURCE='import java.io.*;\nimport java.sql.*;\nimport org.flywaydb.core.Flyway;\npublic class CinenestConnectivityProbe {\n public static void main(String[] args) {\n  try { execute(args); } catch(Throwable failure) { System.err.println("CineNest qualification failed;details withheld to protect credentials"); System.exit(1); }\n }\n static void execute(String[] args) throws Exception {\n  if(args[0].equals("DEPENDENCIES")){System.out.println(Flyway.configure().getClass().getName());return;}\n  String url=System.getenv("CINENEST_DB_URL"),user=System.getenv("CINENEST_DB_USER"),password=System.getenv("CINENEST_DB_PASSWORD");\n  if(url==null||user==null||password==null||password.isBlank())throw new IllegalArgumentException();\n  if(!url.equals("jdbc:postgresql://ddc7a2d85eb1.18o4rx1o80d5g.us-east1.sql.goog:5432/cinenest_connectivity_qualification?sslmode=verify-full&sslrootcert=/run/secrets/db-ca.pem"))throw new IllegalArgumentException("Unexpected isolated JDBC target");\n  if(!user.equals("cinenest_qualification_runtime")&&!user.equals("cinenest_qualification_migrator"))throw new IllegalArgumentException("Unexpected SQL role");\n\n  try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()){\n   var r=s.executeQuery("SELECT current_user,ssl FROM pg_stat_ssl WHERE pid=pg_backend_pid()");if(!r.next()||!user.equals(r.getString(1))||!r.getBoolean(2))throw new IllegalStateException("Identity/TLS mismatch");\n  }\n  if(args[0].equals("PROBE")){\n   if(!user.equals("cinenest_qualification_runtime"))throw new IllegalArgumentException();\n   try(var c=DriverManager.getConnection(url,user,password);var st=c.createStatement()){\n    var rr=st.executeQuery("SELECT count(*) FROM public.flyway_schema_history WHERE success AND version IS NOT NULL");rr.next();if(rr.getInt(1)!=44)throw new IllegalStateException();\n    rr=st.executeQuery("SELECT NOT rolsuper AND NOT rolcreatedb AND NOT rolcreaterole AND NOT rolreplication AND NOT rolbypassrls AND NOT has_database_privilege(current_user,current_database(),\'CREATE\') AND NOT has_database_privilege(current_user,current_database(),\'TEMP\') AND NOT pg_has_role(current_user,\'cinenest_qualification_migrator\',\'MEMBER\') FROM pg_roles WHERE rolname=current_user");if(!rr.next()||!rr.getBoolean(1))throw new IllegalStateException();\n   }\n   try(var c=DriverManager.getConnection(url,user,password);var st=c.createStatement()){\n    for(String deniedSql:new String[]{"CREATE TABLE public.cinenest_qualification_permission_probe(id integer)","CREATE TEMP TABLE cinenest_qualification_permission_probe(id integer)","CREATE ROLE cinenest_qualification_must_be_denied NOLOGIN","SELECT * FROM cinenest_identity.account","SET ROLE cinenest_qualification_migrator"}){\n     c.setAutoCommit(false);try{st.execute(deniedSql);throw new IllegalStateException("Unexpected SQL authorization");}catch(SQLException error){if(!"42501".equals(error.getSQLState()))throw error;}finally{c.rollback();c.setAutoCommit(true);}\n    }\n   }\n   var target=java.net.URI.create(System.getenv("CINENEST_QUALIFICATION_SERVICE_URL"));\n   if(!target.getScheme().equals("https")||target.getUserInfo()!=null||target.getQuery()!=null||target.getFragment()!=null||!target.getHost().startsWith("cinenest-connectivity-qualification-")||!target.getHost().endsWith(".run.app")||(!target.getPath().isEmpty()&&!target.getPath().equals("/")))throw new IllegalArgumentException();\n   var client=java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).followRedirects(java.net.http.HttpClient.Redirect.NEVER).build();\n   var metadata=java.net.URI.create("http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/identity?audience="+java.net.URLEncoder.encode(target.toString(),java.nio.charset.StandardCharsets.UTF_8)+"&format=full");\n   var tokenResult=client.send(java.net.http.HttpRequest.newBuilder(metadata).timeout(java.time.Duration.ofSeconds(10)).header("Metadata-Flavor","Google").GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());if(tokenResult.statusCode()!=200)throw new IllegalStateException();String token=tokenResult.body();\n   String html=null;\n   for(String path:new String[]{"/healthz","/","/api/accounts/me"}){\n    var response=client.send(java.net.http.HttpRequest.newBuilder(target.resolve(path)).timeout(java.time.Duration.ofSeconds(15)).header("Authorization","Bearer "+token).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());\n    if(path.equals("/api/accounts/me")){if(response.statusCode()!=401&&response.statusCode()!=403)throw new IllegalStateException();}\n    else if(response.statusCode()!=200)throw new IllegalStateException();\n    if(path.equals("/"))html=response.body();\n    if(path.equals("/healthz")&&!response.body().contains("UP"))throw new IllegalStateException();\n   }\n   var asset=java.util.regex.Pattern.compile("/_next/static/[^\\\\\\"\'<>\\\\s]+").matcher(html);if(!asset.find())throw new IllegalStateException();\n   var staticResponse=client.send(java.net.http.HttpRequest.newBuilder(target.resolve(asset.group())).timeout(java.time.Duration.ofSeconds(15)).header("Authorization","Bearer "+token).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofByteArray());if(staticResponse.statusCode()!=200||staticResponse.body().length==0)throw new IllegalStateException();\n   var denied=client.send(java.net.http.HttpRequest.newBuilder(target.resolve("/api/session/logout")).timeout(java.time.Duration.ofSeconds(15)).header("Authorization","Bearer "+token).header("Origin","https://wrong.example").POST(java.net.http.HttpRequest.BodyPublishers.noBody()).build(),java.net.http.HttpResponse.BodyHandlers.discarding());if(denied.statusCode()!=403)throw new IllegalStateException();\n   token=null;System.out.println("PRIVATE_JDBC_RUNTIME_AND_AUTHENTICATED_HTTP_PROBE:PASS");return;\n  }\n\n  if(args[0].equals("MIGRATE")){\n   if(!user.equals("cinenest_qualification_migrator"))throw new IllegalArgumentException();\n   var f=Flyway.configure().dataSource(url,user,password).locations("filesystem:"+args[1]).cleanDisabled(true).load();\n   if(f.migrate().migrationsExecuted!=44)throw new IllegalStateException("Expected44migrations");f.validate();\n   if(!f.info().current().getVersion().toString().equals("45")||f.info().pending().length!=0)throw new IllegalStateException("Wrong Flyway state");\n   try(var c=DriverManager.getConnection(url,user,password);var st=c.createStatement()){st.execute(java.nio.file.Files.readString(java.nio.file.Path.of("/app/sql/02-post-migration-runtime-grants.sql.proposed")));}\n   System.out.println("MIGRATE_VALIDATE_AND_GRANTS:44:V45:PASS");return;\n  }\n\n  throw new IllegalArgumentException("Unexpected mode");\n }\n}\n'

def fingerprint(path):
 p=Path(path)
 if p.is_symlink() or not p.is_file():raise ValueError('Regular non-symlink source required')
 return hashlib.sha256(p.read_bytes()).hexdigest()

def migration_inventory(root=ROOT):
 p=Path(root)/'backend/src/main/resources/db/migration'
 items={x.name:fingerprint(x) for x in p.iterdir()}
 versions=sorted(int(re.fullmatch(r'V([0-9]+)__[^/]+\.sql',n)[1]) for n in items)
 if versions!=list(range(1,31))+list(range(32,46)):raise ValueError('Historical migration inventory changed')
 if hashlib.sha256(json.dumps(items,sort_keys=True,separators=(',',':')).encode()).hexdigest()!=MIGRATION_AGGREGATE_SHA256:raise ValueError('Historical migration bytes changed')
 return items

def admission_errors(state):
 errors=[]
 if state.get('productionAuthorization')!='NONE' or state.get('part1')!={'completed':4,'total':7}:errors.append('Production/formal acceptance boundary')
 if state.get('authority',{}).get('requestSha256')!=APPROVAL_SHA256:errors.append('Exact activation authorization')
 r=state.get('review',{})
 if r.get('answer')!='Approve task-only review exception' or r.get('verifiedByteIdenticalFullReviewReuse')!=100 or r.get('taskScopedChangedFileReview')!=14 or r.get('fullCurrentKbReviewClaimed') is not False or r.get('taskOnly') is not True:errors.append('Review exception boundary')
 if state.get('sqlFingerprints')!=SQL_FILES:errors.append('Reviewed SQL fingerprints')
 return errors

def runtime_ready(state,evidence):
 errors=admission_errors(state)
 if state.get('status')!='READY':errors.append('Source admission not READY')
 if state.get('qualification',{}).get('status')!='OFFLINE_TESTS_PASS':errors.append('Source not qualified')
 for key in ['localActivation','localFailureControls','sourceGovernance','hostedExactSourceCI']:
  if evidence.get(key)!='PASS':errors.append('Missing '+key)
 from datetime import datetime,timezone
 try:
  started=datetime.fromisoformat(evidence.get('preparationStartedAt',''))
  if started.tzinfo is None:raise ValueError()
  elapsed=(datetime.now(timezone.utc)-started).total_seconds()
  if not 0<=elapsed<=3600:raise ValueError()
 except (ValueError,TypeError,OverflowError):errors.append('Preparation window unavailable or expired')
 if evidence.get('cloudSessionConsumed') is not False:errors.append('Cloud session unavailable')
 if evidence.get('remainingUsd')!=1 or evidence.get('priorReservedUsd')!=4:errors.append('Budget reconciliation required')
 return {'status':'BLOCKED' if errors else 'ELIGIBLE_CONDITIONAL_OPERATION','errors':errors,'cloudRunConnectivity':'NOT_RUN','productionAuthorization':'NONE'}

def prepare_context(destination,sql_root,dependency_root):
 dest=Path(destination);sql=Path(sql_root);dep=Path(dependency_root)
 if not dest.is_absolute() or dest.exists() or not sql.is_absolute() or not dep.is_absolute():raise ValueError('Exclusive absolute artifact paths required')
 migrations=migration_inventory()
 for n,h in SQL_FILES.items():
  if fingerprint(sql/n)!=h:raise ValueError('Reviewed SQL changed')
 for n,h in DEPENDENCIES.items():
  if fingerprint(dep/n)!=h:raise ValueError('Qualified dependency changed')
 dest.mkdir(mode=0o700);(dest/'lib').mkdir();(dest/'migrations').mkdir();(dest/'sql').mkdir()
 try:
  for n in DEPENDENCIES:shutil.copyfile(dep/n,dest/'lib'/Path(n).name)
  for n in migrations:shutil.copyfile(ROOT/'backend/src/main/resources/db/migration'/n,dest/'migrations'/n)
  shutil.copyfile(sql/'02-post-migration-runtime-grants.sql.proposed',dest/'sql/02-post-migration-runtime-grants.sql.proposed')
  (dest/'CinenestConnectivityProbe.java').write_text(JAVA_SOURCE)
  recipe=f"FROM {JDK} AS build\nWORKDIR /build\nCOPY lib/ lib/\nCOPY CinenestConnectivityProbe.java .\nRUN mkdir classes && javac -proc:none -cp '/build/lib/*' -d classes CinenestConnectivityProbe.java\nFROM {JRE}\nWORKDIR /app\nCOPY --from=build --chown=10001:10001 /build/classes/ /app/classes/\nCOPY --chown=10001:10001 lib/ /app/lib/\nCOPY --chown=10001:10001 migrations/ /app/migrations/\nCOPY --chown=10001:10001 sql/ /app/sql/\nUSER 10001:10001\nENTRYPOINT [\"java\",\"-Xmx256m\",\"-cp\",\"/app/classes:/app/lib/*\",\"CinenestConnectivityProbe\"]\nCMD [\"PROBE\"]\n"
  (dest/'Dockerfile').write_text(recipe)
  manifest={str(p.relative_to(dest)):fingerprint(p) for p in dest.rglob('*') if p.is_file()}
  (dest/'context-sha256.json').write_text(json.dumps(manifest,indent=2)+'\n')
 except BaseException:
  # Exclusive new directory,never delete a caller's existing directory.
  shutil.rmtree(dest);raise
 return {'status':'PREPARED_OFFLINE_ONLY','files':manifest,'cloudRunConnectivity':'NOT_RUN','productionAuthorization':'NONE'}

def job_document(mode,image,versions,service_url=None):
 if mode not in ['MIGRATE','PROBE']:raise ValueError('Reviewed job mode required')
 prefix='us-east1-docker.pkg.dev/'+PROJECT+'/cinenest-runtime/diagnostics@sha256:'
 if not image.startswith(prefix) or not re.fullmatch('[a-f0-9]{64}',image[len(prefix):]):raise ValueError('Immutable approved diagnostic image required')
 if set(versions)!={'runtimeUsername','runtimePassword','migrationPassword','databaseCa'} or any(v!='1' for v in versions.values()):raise ValueError('New enabled numeric secret version1 evidence required')
 from urllib.parse import urlsplit
 if mode=='PROBE':
  u=urlsplit(service_url or '')
  if u.scheme!='https' or not u.hostname or not u.hostname.startswith('cinenest-connectivity-qualification-') or not u.hostname.endswith('.run.app') or u.username or u.password or u.query or u.fragment or u.path not in ['','/']:raise ValueError('Verified internal service URL required')
 obj=json.loads('\n'.join(x for x in (ROOT/'deployment/cloud-run/connectivity-job.template.yaml').read_text().splitlines() if not x.startswith('#')))
 pod=obj['spec']['template']['spec']['template']['spec'];ann=obj['spec']['template']['metadata']['annotations'];obj['metadata']['name']='cinenest-connectivity-'+('migration' if mode=='MIGRATE' else 'probe')
 pod['serviceAccountName']=('cinenest-migrator' if mode=='MIGRATE' else 'cinenest-connectivity-probe')+'@'+PROJECT+'.iam.gserviceaccount.com'
 ann['run.googleapis.com/vpc-access-egress']='private-ranges-only' if mode=='MIGRATE' else 'all-traffic'
 c=pod['containers'][0];c['image']=image;c['args']=[mode,'/app/migrations']
 user={'name':'CINENEST_DB_USER','value':'cinenest_qualification_migrator'} if mode=='MIGRATE' else {'name':'CINENEST_DB_USER','valueFrom':{'secretKeyRef':{'name':'cinenest-qualification-db-user','key':'1'}}}
 c['env']=[{'name':'CINENEST_DB_URL','value':'jdbc:postgresql://ddc7a2d85eb1.18o4rx1o80d5g.us-east1.sql.goog:5432/cinenest_connectivity_qualification?sslmode=verify-full&sslrootcert=/run/secrets/db-ca.pem'},user,{'name':'CINENEST_DB_PASSWORD','valueFrom':{'secretKeyRef':{'name':'cinenest-qualification-migration-password' if mode=='MIGRATE' else 'cinenest-qualification-db-password','key':'1'}}}]
 if mode=='PROBE':c['env'].append({'name':'CINENEST_QUALIFICATION_SERVICE_URL','value':service_url})
 return obj

def main(argv=None):
 p=argparse.ArgumentParser(description='Offline diagnostic artifact preparation;never deploys or reads secrets')
 p.add_argument('--prepare-context',type=Path);p.add_argument('--sql-root',type=Path);p.add_argument('--dependency-root',type=Path);a=p.parse_args(argv)
 try:
  if a.prepare_context:
   if not a.sql_root or not a.dependency_root:raise ValueError('Reviewed source roots required')
   result=prepare_context(a.prepare_context,a.sql_root,a.dependency_root)
  else:result={'status':'OFFLINE_ONLY','migrations':len(migration_inventory()),'cloudRunConnectivity':'NOT_RUN','productionAuthorization':'NONE'}
  print(json.dumps(result,indent=2));return 0
 except (OSError,ValueError,TypeError,KeyError):print('Qualification artifact unavailable or denied',file=sys.stderr);return 1

if __name__=='__main__':raise SystemExit(main())
