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
JAVA_SOURCE='import java.io.*;\nimport java.sql.*;\nimport org.flywaydb.core.Flyway;\npublic class CinenestConnectivityProbe {\n public static void main(String[] args) {\n  try { execute(args); } catch(Throwable failure) { System.err.println("CineNest qualification failed;details withheld to protect credentials"); System.exit(1); }\n }\n static void execute(String[] args) throws Exception {\n  if(args[0].equals("DEPENDENCIES")){System.out.println(Flyway.configure().getClass().getName());return;}\n  String url=System.getenv("CINENEST_DB_URL"),user=System.getenv("CINENEST_DB_USER"),password=System.getenv("CINENEST_DB_PASSWORD");\n  if(url==null||user==null||password==null||password.isBlank())throw new IllegalArgumentException();\n  if(!url.equals("jdbc:postgresql://ddc7a2d85eb1.18o4rx1o80d5g.us-east1.sql.goog.:5432/cinenest_connectivity_qualification?sslmode=verify-full&sslrootcert=/run/secrets/db-ca.pem"))throw new IllegalArgumentException("Unexpected isolated JDBC target");\n  if(!user.equals("cinenest_qualification_runtime")&&!user.equals("cinenest_qualification_migrator"))throw new IllegalArgumentException("Unexpected SQL role");\n\n  try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()){\n   var r=s.executeQuery("SELECT current_user,ssl FROM pg_stat_ssl WHERE pid=pg_backend_pid()");if(!r.next()||!user.equals(r.getString(1))||!r.getBoolean(2))throw new IllegalStateException("Identity/TLS mismatch");\n  }\n  if(args[0].equals("PROBE")){\n   if(!user.equals("cinenest_qualification_runtime"))throw new IllegalArgumentException();\n   try(var c=DriverManager.getConnection(url,user,password);var st=c.createStatement()){\n    var rr=st.executeQuery("SELECT count(*) FROM public.flyway_schema_history WHERE success AND version IS NOT NULL");rr.next();if(rr.getInt(1)!=44)throw new IllegalStateException();\n    rr=st.executeQuery("SELECT NOT rolsuper AND NOT rolcreatedb AND NOT rolcreaterole AND NOT rolreplication AND NOT rolbypassrls AND NOT has_database_privilege(current_user,current_database(),\'CREATE\') AND NOT has_database_privilege(current_user,current_database(),\'TEMP\') AND NOT pg_has_role(current_user,\'cinenest_qualification_migrator\',\'MEMBER\') FROM pg_roles WHERE rolname=current_user");if(!rr.next()||!rr.getBoolean(1))throw new IllegalStateException();\n   }\n   try(var c=DriverManager.getConnection(url,user,password);var st=c.createStatement()){\n    for(String deniedSql:new String[]{"CREATE TABLE public.cinenest_qualification_permission_probe(id integer)","CREATE TEMP TABLE cinenest_qualification_permission_probe(id integer)","CREATE ROLE cinenest_qualification_must_be_denied NOLOGIN","SELECT * FROM cinenest_identity.account","SET ROLE cinenest_qualification_migrator"}){\n     c.setAutoCommit(false);try{st.execute(deniedSql);throw new IllegalStateException("Unexpected SQL authorization");}catch(SQLException error){if(!"42501".equals(error.getSQLState()))throw error;}finally{c.rollback();c.setAutoCommit(true);}\n    }\n   }\n   var target=java.net.URI.create(System.getenv("CINENEST_QUALIFICATION_SERVICE_URL"));\n   if(!target.getScheme().equals("https")||target.getUserInfo()!=null||target.getQuery()!=null||target.getFragment()!=null||!target.getHost().startsWith("cinenest-connectivity-qualification-")||!target.getHost().endsWith(".run.app")||(!target.getPath().isEmpty()&&!target.getPath().equals("/")))throw new IllegalArgumentException();\n   var client=java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).followRedirects(java.net.http.HttpClient.Redirect.NEVER).build();\n   var metadata=java.net.URI.create("http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/identity?audience="+java.net.URLEncoder.encode(target.toString(),java.nio.charset.StandardCharsets.UTF_8)+"&format=full");\n   var tokenResult=client.send(java.net.http.HttpRequest.newBuilder(metadata).timeout(java.time.Duration.ofSeconds(10)).header("Metadata-Flavor","Google").GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());if(tokenResult.statusCode()!=200)throw new IllegalStateException();String token=tokenResult.body();\n   String html=null;\n   for(String path:new String[]{"/healthz","/","/api/accounts/me"}){\n    var response=client.send(java.net.http.HttpRequest.newBuilder(target.resolve(path)).timeout(java.time.Duration.ofSeconds(15)).header("Authorization","Bearer "+token).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());\n    if(path.equals("/api/accounts/me")){if(response.statusCode()!=401&&response.statusCode()!=403)throw new IllegalStateException();}\n    else if(response.statusCode()!=200)throw new IllegalStateException();\n    if(path.equals("/"))html=response.body();\n    if(path.equals("/healthz")&&!response.body().contains("UP"))throw new IllegalStateException();\n   }\n   var asset=java.util.regex.Pattern.compile("/_next/static/[^\\\\\\"\'<>\\\\s]+").matcher(html);if(!asset.find())throw new IllegalStateException();\n   var staticResponse=client.send(java.net.http.HttpRequest.newBuilder(target.resolve(asset.group())).timeout(java.time.Duration.ofSeconds(15)).header("Authorization","Bearer "+token).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofByteArray());if(staticResponse.statusCode()!=200||staticResponse.body().length==0)throw new IllegalStateException();\n   var denied=client.send(java.net.http.HttpRequest.newBuilder(target.resolve("/api/session/logout")).timeout(java.time.Duration.ofSeconds(15)).header("Authorization","Bearer "+token).header("Origin","https://wrong.example").POST(java.net.http.HttpRequest.BodyPublishers.noBody()).build(),java.net.http.HttpResponse.BodyHandlers.discarding());if(denied.statusCode()!=403)throw new IllegalStateException();\n   token=null;System.out.println("PRIVATE_JDBC_RUNTIME_AND_AUTHENTICATED_HTTP_PROBE:PASS");return;\n  }\n\n  if(args[0].equals("MIGRATE")){\n   if(!user.equals("cinenest_qualification_migrator"))throw new IllegalArgumentException();\n   var f=Flyway.configure().dataSource(url,user,password).locations("filesystem:"+args[1]).cleanDisabled(true).load();\n   if(f.migrate().migrationsExecuted!=44)throw new IllegalStateException("Expected44migrations");f.validate();\n   if(!f.info().current().getVersion().toString().equals("45")||f.info().pending().length!=0)throw new IllegalStateException("Wrong Flyway state");\n   try(var c=DriverManager.getConnection(url,user,password);var st=c.createStatement()){st.execute(java.nio.file.Files.readString(java.nio.file.Path.of("/app/sql/02-post-migration-runtime-grants.sql.proposed")));}\n   System.out.println("MIGRATE_VALIDATE_AND_GRANTS:44:V45:PASS");return;\n  }\n\n  throw new IllegalArgumentException("Unexpected mode");\n }\n}\n'

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
 c['env']=[{'name':'CINENEST_DB_URL','value':'jdbc:postgresql://ddc7a2d85eb1.18o4rx1o80d5g.us-east1.sql.goog.:5432/cinenest_connectivity_qualification?sslmode=verify-full&sslrootcert=/run/secrets/db-ca.pem'},user,{'name':'CINENEST_DB_PASSWORD','valueFrom':{'secretKeyRef':{'name':'cinenest-qualification-migration-password' if mode=='MIGRATE' else 'cinenest-qualification-db-password','key':'1'}}}]
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



# Pure, non-production confirmation contract;never grants roles or contacts GCP.
from datetime import datetime,timezone
import uuid

MANAGED_PRINCIPAL='cinenest44@gmail.com'
MANAGED_ROLE='cloudsqlsuperuser'
MANAGED_INSTANCE='cine-nest-2026'
MANAGED_PROJECT='project-f903a5f6-f535-4016-aae'
AUTHORITY_PROBE_SHA256='cd2a892c70da4197cacc61e815419ed7014de0f441606c2cc40b851a78c28c2d'

def managed_role_metadata(user):
    """Missing optional metadata is UNKNOWN,not an observed empty assignment."""
    if not isinstance(user,dict) or user.get('name')!=MANAGED_PRINCIPAL or user.get('type')!='CLOUD_IAM_USER':
        raise ValueError('Wrong managed-role principal')
    for key,expected in [('project',MANAGED_PROJECT),('instance',MANAGED_INSTANCE)]:
        if key in user and user[key]!=expected:raise ValueError('Wrong managed-role scope')
    if 'databaseRoles' not in user:return None
    roles=user['databaseRoles']
    if not isinstance(roles,list) or any(not isinstance(x,str) for x in roles) or len(set(roles))!=len(roles):
        raise ValueError('Malformed managed-role metadata')
    if set(roles)-{MANAGED_ROLE}:raise ValueError('Unapproved managed-role assignment')
    return sorted(roles)

def managed_role_query(nonce):
    """Read-only catalog query;no password catalogs,no SQL grants or role changes."""
    if str(uuid.UUID(nonce))!=nonce:raise ValueError('Canonical fresh nonce required')
    return """SELECT json_build_object(
      'nonce','%s','observedAt',clock_timestamp(),
      'currentUser',current_user,'sessionUser',session_user,'database',current_database(),
      'tls',COALESCE((SELECT ssl FROM pg_stat_ssl WHERE pid=pg_backend_pid()),false),
      'member',pg_has_role(session_user,'cloudsqlsuperuser','MEMBER'),
      'canSet',pg_has_role(session_user,'cloudsqlsuperuser','SET'),
      'runtimeAdmin',pg_has_role(current_user,'cinenest_qualification_runtime','MEMBER WITH ADMIN OPTION'),
      'migratorAdmin',pg_has_role(current_user,'cinenest_qualification_migrator','MEMBER WITH ADMIN OPTION'),
      'restricted',(SELECT NOT rolsuper AND NOT rolcreatedb AND NOT rolcreaterole AND NOT rolreplication AND NOT rolbypassrls FROM pg_roles WHERE rolname=session_user),
      'edges',COALESCE((SELECT json_agg(json_build_object('role',r.rolname,'member',m.rolname,'grantor',g.rolname,'admin',a.admin_option,'inherit',a.inherit_option,'set',a.set_option)) FROM pg_auth_members a JOIN pg_roles r ON r.oid=a.roleid JOIN pg_roles m ON m.oid=a.member JOIN pg_roles g ON g.oid=a.grantor WHERE r.rolname='cloudsqlsuperuser' AND m.rolname=session_user),'[]'::json)
    )""" % nonce

def managed_role_confirmation(user,sql,context,phase,now=None):
    """Validate actual fresh SQL facts plus scoped API identity;metadata alone never passes.
    Supplied snapshots are not execution evidence. Callers must use a new authenticated
    connection after the completed mutation,keep the independent revoker armed,and run
    unchanged reviewed probe00 before any credential or activation action.
    """
    errors=[];roles=None
    try:roles=managed_role_metadata(user)
    except (ValueError,TypeError):errors.append('Managed-role identity/metadata unavailable or unsafe')
    try:
        if phase not in ['DELEGATED','REVOKED']:raise ValueError()
        if not isinstance(context,dict) or context.get('project')!=MANAGED_PROJECT or context.get('instance')!=MANAGED_INSTANCE or context.get('principal')!=MANAGED_PRINCIPAL:raise ValueError()
        nonce=context['nonce']
        if str(uuid.UUID(nonce))!=nonce:raise ValueError()
        if context.get('sslmode')!='verify-full' or context.get('host')!='ddc7a2d85eb1.18o4rx1o80d5g.us-east1.sql.goog.' or context.get('hostaddr')!='10.40.32.2' or context.get('caSha256')!='b9c1735f6619bc814020948a0da2e8970efa76f7d885fd28dbe1c21a85193f46':raise ValueError()
        operation=context['operation']
        if operation.get('status')!='DONE' or operation.get('error') or not operation.get('id') or operation.get('phase')!=phase:raise ValueError()
        current=now or datetime.now(timezone.utc)
        completed=datetime.fromisoformat(operation['completedAt']);observed=datetime.fromisoformat(sql['observedAt'])
        if any(x.tzinfo is None for x in [current,completed,observed]) or not 0<=(current-observed).total_seconds()<=10 or not completed<=observed<=current:raise ValueError()
        if sql.get('nonce')!=nonce or sql.get('sessionUser')!=MANAGED_PRINCIPAL or sql.get('database')!='postgres' or sql.get('tls') is not True:raise ValueError()
        if not isinstance(sql.get('edges'),list):raise ValueError()
        for edge in sql['edges']:
            if edge.get('role')!=MANAGED_ROLE or edge.get('member')!=MANAGED_PRINCIPAL or not edge.get('grantor') or any(type(edge.get(x)) is not bool for x in ['admin','inherit','set']):raise ValueError()
        if phase=='DELEGATED':
            if roles is not None and roles!=[MANAGED_ROLE]:raise ValueError()
            if context.get('revokerArmed') is not True or context.get('probeSha256')!=AUTHORITY_PROBE_SHA256 or context.get('probePassed') is not True:raise ValueError()
            if sql.get('currentUser')!=MANAGED_ROLE or any(sql.get(x) is not True for x in ['member','canSet','runtimeAdmin','migratorAdmin']):raise ValueError()
        else:
            if roles is not None and roles!=[]:raise ValueError()
            if sql.get('currentUser')!=MANAGED_PRINCIPAL or sql.get('restricted') is not True or sql['edges'] or any(sql.get(x) is not False for x in ['member','canSet','runtimeAdmin','migratorAdmin']):raise ValueError()
    except (ValueError,TypeError,KeyError,AttributeError,OverflowError):errors.append('Fresh scoped PostgreSQL authority not confirmed')
    return {'status':'DENIED' if errors else 'CONFIRMED','phase':phase,'metadata':'UNKNOWN_OPTIONAL_FIELD' if roles is None else 'OBSERVED','errors':errors,'productionAuthorization':'NONE'}

def managed_role_lifecycle(grant,confirm,revoke,confirm_revoked,action):
    """Even failed entry/confirmation always invokes revocation;never relies on __exit__.
    The externally verified independent revoker remains mandatory. Cleanup failures
    propagate and prevent success,including when the body also failed.
    """
    try:
        grant()
        if confirm()['status']!='CONFIRMED':raise RuntimeError('Managed authority not confirmed')
        return action()
    finally:
        revoke()
        if confirm_revoked()['status']!='CONFIRMED':raise RuntimeError('Managed authority revocation not confirmed')



def managed_assignment_query(nonce):
    """Read-only control-plane SQL metadata;not proof of private TLS or authority."""
    query=managed_role_query(nonce)
    target="r.rolname='cloudsqlsuperuser' AND m.rolname=session_user"
    if query.count(target)!=1:raise ValueError('Unexpected membership query shape')
    return query.replace(target,'m.rolname=session_user')

def managed_assignment_roles(sql,nonce,now=None):
    """Use fresh authenticated catalogs when optional User.databaseRoles is absent.
    Only the existing IAM system group and approved temporary managed group are
    accepted. An unknown concurrent membership blocks wholesale role replacement.
    This metadata check never substitutes for private verify-full/probe00.
    """
    try:
        if str(uuid.UUID(nonce))!=nonce:raise ValueError()
        observed=datetime.fromisoformat(sql['observedAt']);current=now or datetime.now(timezone.utc)
        if observed.tzinfo is None or current.tzinfo is None or not 0<=(current-observed).total_seconds()<=10:raise ValueError()
        if sql.get('nonce')!=nonce or sql.get('sessionUser')!=MANAGED_PRINCIPAL or sql.get('currentUser')!=MANAGED_PRINCIPAL or sql.get('database')!='postgres':raise ValueError()
        edges=sql['edges']
        if not isinstance(edges,list):raise ValueError()
        names=[]
        for edge in edges:
            if edge.get('member')!=MANAGED_PRINCIPAL or edge.get('role') not in ['cloudsqliamuser',MANAGED_ROLE] or not edge.get('grantor') or any(type(edge.get(x)) is not bool for x in ['admin','inherit','set']):raise ValueError()
            names.append(edge['role'])
        if len(names)!=len(set(names)):raise ValueError()
        present=MANAGED_ROLE in names
        if sql.get('member') is not present:raise ValueError()
        return [MANAGED_ROLE] if present else []
    except (ValueError,KeyError,TypeError,AttributeError):raise ValueError('Fresh scoped SQL assignment metadata unavailable or unsafe') from None



def wait_managed_assignment(fetch,expected,timeout=10,clock=None,pause=None):
    """Bound only observation latency;never grants authority or retries a mutation.
    fetch must obtain a new nonce-bound authenticated catalog snapshot each time.
    Unsafe/malformed evidence fails immediately;fresh contrary assignments may be
    observed again within the existing burst deadline. No cached positive result.
    """
    import time,math
    if expected not in [[],[MANAGED_ROLE]] or type(timeout) not in [int,float] or not math.isfinite(timeout) or not 0<timeout<=15:raise ValueError('Bounded expected assignment required')
    clock=clock or time.monotonic;pause=pause or time.sleep;end=clock()+timeout
    while clock()<end:
        actual=fetch()
        if actual==expected:return actual
        if actual not in [[],[MANAGED_ROLE]]:raise ValueError('Unsafe assignment observation')
        pause(min(.25,max(0,end-clock())))
    raise TimeoutError('Managed assignment not confirmed within observation deadline')



# CN-GCP-SQL-STAGING-TOKEN-SAFETY-001: offline guards; no execution authority.
"""Offline correction candidate. No cloud or SQL mutation is performed here."""
import contextlib
import datetime as credential_datetime
import hashlib
import json
import math
import os
from pathlib import Path
import re
import shutil
import stat
import subprocess
import tempfile
import time

PRINCIPAL = 'cinenest44@gmail.com'


def approved_sql_manifest(raw, contract):
    """Preserve order and reject duplicates before constructing a dictionary."""
    try:
        def pairs(rows):
            if len({k for k, _ in rows}) != len(rows):
                raise ValueError()
            return rows
        rows = json.loads(raw, object_pairs_hook=pairs)
        if not isinstance(rows, list) or len(rows) != 7:
            raise ValueError()
        if rows != list(contract.items()):
            raise ValueError()
        if any(Path(n).name != n or not re.fullmatch(r'[0-9a-f]{64}', h)
               for n, h in rows):
            raise ValueError()
        return tuple(tuple(row) for row in rows)
    except (ValueError, TypeError, AttributeError):
        raise ValueError('Exact ordered seven-file SQL manifest required') from None


def sql_input_names(directory):
    return {p.name for p in Path(directory).iterdir()
            if p.name.endswith(('.sql.proposed', '.psql.proposed'))}


@contextlib.contextmanager
def stage_approved_sql(source, parent, manifest, contract):
    """Copy only verified bytes into a private, automatically removed directory.

    The caller must complete this preflight before acquiring credentials or
    granting authority. Inputs are never moved, overwritten or deleted.
    """
    rows = approved_sql_manifest(manifest, contract)
    source, parent = Path(source), Path(parent)
    if source.is_symlink() or parent.is_symlink():
        raise ValueError('Unsafe staging directory')
    if sql_input_names(source) != {n for n, _ in rows}:
        raise ValueError('Missing or unexpected SQL input')
    with tempfile.TemporaryDirectory(prefix='cinenest-approved-sql-', dir=parent) as tmp:
        target = Path(tmp)
        os.chmod(target, 0o700)
        for name, expected in rows:
            fd = os.open(source / name, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
            try:
                meta = os.fstat(fd)
                if not stat.S_ISREG(meta.st_mode) or not 0 < meta.st_size <= 1024 * 1024:
                    raise ValueError('Unsafe SQL input type or size')
                with os.fdopen(fd, 'rb', closefd=False) as stream:
                    data = stream.read(1024 * 1024 + 1)
                if len(data) != meta.st_size or hashlib.sha256(data).hexdigest() != expected:
                    raise ValueError('SQL fingerprint mismatch')
            finally:
                os.close(fd)
            out = os.open(target / name, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            with os.fdopen(out, 'wb') as stream:
                stream.write(data)
        # Detect an unexpected input introduced during staging too.
        if sql_input_names(source) != {n for n, _ in rows}:
            raise ValueError('SQL inventory changed during staging')
        if set(p.name for p in target.iterdir()) != {n for n, _ in rows}:
            raise ValueError('Unexpected staged input')
        for name, expected in rows:
            if hashlib.sha256((target / name).read_bytes()).hexdigest() != expected:
                raise ValueError('Staged SQL fingerprint mismatch')
        yield target, tuple(name for name, _ in rows)


class ProtectedRequestFailure(RuntimeError):
    """Only a validated HTTP status may cross the protected error boundary."""
    def __init__(self, status):
        if type(status) is not int or not 400 <= status <= 599:
            raise ValueError('Invalid protected HTTP status')
        self.status = status
        super().__init__('Protected request rejected; HTTP ' + str(status))


class ProtectedIamCredential:
    """Opaque in-memory credential with authoritative UTC expiry, never a TTL guess."""
    __slots__ = ('_token', '_expiry', '_issued', '_monotonic', '_clock', '_ticks')

    def __init__(self, token, expiry, principal, clock=None, ticks=None):
        self._token = None
        self._clock = clock or (lambda: credential_datetime.datetime.now(credential_datetime.timezone.utc))
        self._ticks = ticks or time.monotonic
        try:
            now = self._clock()
            end = credential_datetime.datetime.strptime(expiry, '%Y-%m-%dT%H:%M:%SZ').replace(tzinfo=credential_datetime.timezone.utc)
            if principal != PRINCIPAL or now.tzinfo is None:
                raise ValueError()
            if not isinstance(token, str) or not 16 <= len(token) <= 16384 or not re.fullmatch(r'[A-Za-z0-9._~+/-]+=*', token):
                raise ValueError()
            if (end - now).total_seconds() > 3630:
                raise ValueError()
            self._issued, self._expiry, self._monotonic = now, end, self._ticks()
            self._token = token
            self.check(120)
        except (ValueError, TypeError, AttributeError, OverflowError):
            self.close()
            raise ValueError('Protected IAM credential unavailable or insufficiently valid') from None

    def __repr__(self):
        return '<ProtectedIamCredential REDACTED>'

    def check(self, required_seconds):
        now, tick = self._clock(), self._ticks()
        if type(required_seconds) not in (int, float) or not math.isfinite(required_seconds) or not 0 <= required_seconds <= 120:
            raise ValueError('Bounded operation duration required')
        if now.tzinfo is None or self._token is None or now < self._issued or tick < self._monotonic:
            raise ValueError('Expired or invalid protected credential')
        elapsed = tick - self._monotonic
        remaining = min((self._expiry - now).total_seconds(),
                        (self._expiry - self._issued).total_seconds() - elapsed)
        if remaining <= required_seconds + 30:
            raise ValueError('Insufficient credential validity before authentication')

    def use(self, sender, required_seconds=120):
        try:
            self.check(required_seconds)  # Immediately before the protected send.
            result = sender(self._token)
            serialized = result if isinstance(result, bytes) else json.dumps(result).encode()
            if self._token.encode() in serialized:
                raise ValueError('Protected response contains credential')
            return result
        except ProtectedRequestFailure:
            raise
        except Exception:
            raise RuntimeError('Protected authentication failed; details withheld') from None
        finally:
            self.close()

    @property
    def expiry(self):
        return self._expiry.strftime('%Y-%m-%dT%H:%M:%SZ')

    def close(self):
        self._token = None


def acquire_protected_iam(purpose, run=None, clock=None, ticks=None):
    """CLI stdout stays in memory. No logging/output-file/secret-bearing argv.

    config-helper is an internal SDK interface: fail closed if its contract
    changes. SQL uses the same downscoping contract as generate-login-token,
    but obtains token and expiry together from one scoped SDK response. A second
    independently refreshed token must never be compared to the first.
    """
    if purpose not in ('API', 'SQL'):
        raise ValueError('Approved credential purpose required')
    run = run or subprocess.run
    token = raw = None
    try:
        if any(os.environ.get(name) for name in ('CLOUDSDK_AUTH_ACCESS_TOKEN',
               'CLOUDSDK_AUTH_ACCESS_TOKEN_FILE', 'CLOUDSDK_AUTH_CREDENTIAL_FILE_OVERRIDE',
               'CLOUDSDK_AUTH_IMPERSONATE_SERVICE_ACCOUNT')):
            raise ValueError()
        args = ['gcloud', 'config', 'config-helper', '--force-auth-refresh',
                '--account=' + PRINCIPAL, '--format=json', '--quiet', '--verbosity=error']
        if purpose == 'SQL':
            args.append('--scopes=https://www.googleapis.com/auth/sqlservice.login')
        environment = dict(os.environ, CLOUDSDK_CORE_LOG_HTTP='false', CLOUDSDK_CORE_VERBOSITY='error')
        q = run(args, capture_output=True, text=True, timeout=25, check=False, env=environment)
        if q.returncode or len(q.stdout) > 65536:
            raise ValueError()
        raw = json.loads(q.stdout)
        properties = raw['configuration']['properties']
        principal = properties['core']['account']
        if any(properties.get('auth', {}).get(name) for name in
               ('impersonate_service_account', 'credential_file_override', 'access_token_file')):
            raise ValueError()
        token = raw['credential']['access_token']
        expiry = raw['credential']['token_expiry']
        return ProtectedIamCredential(token, expiry, principal, clock, ticks)
    except Exception:
        raise RuntimeError('Protected IAM acquisition or expiry verification failed') from None
    finally:
        token = raw = None


def authenticated_operation(acquire, send, kind, required_seconds=120):
    """No automatic replay, including after 401 or an uncertain response.

    Cleanup/revocation must reacquire its own credential and verify actual state;
    it must not depend on a cached credential from the failed operation.
    """
    if kind not in ('READ_ONLY', 'MUTATION'):
        raise ValueError('Explicit operation classification required')
    credential = acquire()
    return credential.use(send, required_seconds)

def install_approved_sql(source, destination, manifest):
    """Install the complete verified bundle before any credential or authority.

    Only the frozen source-owned SQL_FILES contract is accepted. No overwrite,
    extension discovery for admission, or partial output is permitted.
    """
    destination = Path(destination)
    if destination.is_symlink() or not destination.is_dir():
        raise ValueError('Private SQL destination required')
    if stat.S_IMODE(destination.stat().st_mode) != 0o700 or sql_input_names(destination):
        raise ValueError('Empty private SQL destination required')
    created = []
    try:
        with stage_approved_sql(source, destination.parent, manifest, SQL_FILES) as (staged, names):
            for name in names:
                # Hard linking verified private files is atomic and refuses overwrite.
                os.link(staged / name, destination / name, follow_symlinks=False)
                created.append(destination / name)
            if sql_input_names(destination) != set(SQL_FILES):
                raise ValueError('Installed SQL inventory mismatch')
            for name, expected in SQL_FILES.items():
                if hashlib.sha256((destination / name).read_bytes()).hexdigest() != expected:
                    raise ValueError('Installed SQL fingerprint mismatch')
        return {'status': 'VERIFIED_PUBLIC_SQL_STAGING', 'orderedFiles': list(SQL_FILES)}
    except BaseException:
        for path in reversed(created):
            path.unlink()
        raise


def sql_staging_command():
    """A public-input-only remote preflight; no token, SQL execution or cloud API."""
    import shlex
    program = ("import runpy;from pathlib import Path;"
               "m=runpy.run_path('cinenest-runtime/managed-role-contract.py');"
               "m['install_approved_sql']('.', 'cinenest-runtime', "
               "Path('cinenest-runtime/approved-sql-hashes.json').read_text())")
    return 'python3 -c ' + shlex.quote(program)


def runtime_safety_artifacts(controller, helpers, private_helper):
    """Offline, fail-closed correction of the retained public execution capsule.

    Generated artifacts stay outside Git and grant no runtime authority. A fresh
    exact fingerprint/session/time/cost admission is still mandatory. Historical
    originals must be retained. Unknown predecessor shapes are rejected.
    """
    def once(text, old, new):
        if text.count(old) != 1:
            raise ValueError('Unexpected retained runtime artifact shape')
        return text.replace(old, new, 1)
    controller = once(controller,
        "managed-role-source.sha256 *.sql.proposed cinenest-runtime/; cd cinenest-runtime;",
        "managed-role-source.sha256 cinenest-runtime/; '+M['sql_staging_command']()+'; cd cinenest-runtime;")
    if controller.count("v['managed_confirmation_errors']()") not in (1, 2):
        raise ValueError('Unexpected retained source-admission gate')
    controller = controller.replace("v['managed_confirmation_errors']()", "v['sql_staging_token_errors']()")
    helpers = once(helpers,
        "E=Path('/tmp/cinenest-role-confirmation-fix-20261122/cloud-candidate');",
        'E=Path(__file__).resolve().parent;')
    start, end = helpers.find('def auth():\n'), helpers.find('def operation(op):\n')
    if start < 0 or end <= start or helpers.count('def auth():\n') != 1 or helpers.count('def api(') != 1:
        raise ValueError('Unexpected retained API transport shape')
    replacement = '''def api(method,url,body=None,missing=False):
 def send(access_token):
  req=urllib.request.Request(url,data=None if body is None else json.dumps(body).encode(),headers={'Authorization':'Bearer '+access_token,'Content-Type':'application/json'},method=method)
  try:
   with urllib.request.urlopen(req,timeout=max(1,min(25,deadline-time.monotonic()-3)) if deadline else 25) as response:data=response.read();return json.loads(data) if data else {}
  except urllib.error.HTTPError as error:
   if missing and error.code==404:return None
   # Never print a secret-bearing response or automatically replay a mutation.
   raise M['ProtectedRequestFailure'](error.code) from None
 return M['authenticated_operation'](lambda:M['acquire_protected_iam']('API'),send,'READ_ONLY' if method=='GET' else 'MUTATION',30)
'''
    helpers = helpers[:start] + replacement + helpers[end:]
    start, end = helpers.find('def private_sql('), helpers.find('def role_context(')
    if start < 0 or end <= start or helpers.count('def private_sql(') != 1:
        raise ValueError('Unexpected retained private SQL transport shape')
    private_send = '''def private_sql(mode,passwords=None,expected_denial=False):
 nonce=str(__import__('uuid').uuid4());credential=M['acquire_protected_iam']('SQL')
 def budget():
  remaining=min(50,state.get('burstActorDeadline',time.monotonic()+50)-time.monotonic()) if mode in ['probe','authority','credentials','activate','cleanup'] else 45;assert remaining>=8,'Admin actor cleanup reserve'
  return remaining
 def send(login_token):
  remaining=budget()
  payload=json.dumps({'mode':mode,'nonce':nonce,'iamToken':login_token,'iamTokenExpiry':credential.expiry,'passwords':passwords or [],'timeoutSeconds':remaining});secret_values.append(login_token)
  try:return run('private-sql-'+mode,ssh+['cd cinenest-runtime; python3 private-sql-helper.py'],timeout=remaining+3,input=payload)
  finally:secret_values.remove(login_token);payload=None
 try:
  remaining=budget()
  code,out,err=credential.use(send,remaining+3)
  result=json.loads(next(x for x in out.splitlines() if x.startswith('{')))
  if expected_denial:assert result['status']=='FAIL' and any(t in result.get('output','').lower() for t in ['not permitted to log in','password authentication failed']),mode+' did not prove authentication denial'
  else:assert result['status']=='PASS',mode
  if mode=='revocation':assert USER+'|'+USER+'|t|t' in result['output']
  result['_requestNonce']=nonce
  return result
 finally:credential.close()
'''
    helpers = helpers[:start] + private_send + helpers[end:]
    private_helper = once(private_helper,
        'fd=None;protected=[];authority_facts=None',
        'fd=None;protected=[];authority_facts=None;credential=None')
    private_helper = once(private_helper,
        "token=request.pop('iamToken');protected.append(token);",
        "token=request.pop('iamToken');expiry=request.pop('iamTokenExpiry');protected.append(token);")
    private_helper = once(private_helper,
        " fd=os.memfd_create('cinenest-protected-passfile',0);",
        " contract_path=E/'managed-role-contract.py';assert hashlib.sha256(contract_path.read_bytes()).hexdigest()==(E/'managed-role-source.sha256').read_text().strip();contract=runpy.run_path(str(contract_path));credential=contract['ProtectedIamCredential'](token,expiry,'cinenest44@gmail.com');credential.check(maximum)\n fd=os.memfd_create('cinenest-protected-passfile',0);")
    private_helper = once(private_helper,
        " if mode=='credentials':\n  assert len(passwords)==2",
        " credential.check(maximum)\n if mode=='credentials':\n  assert len(passwords)==2")
    private_helper = once(private_helper,
        ' if fd is not None:os.close(fd)\n protected.clear()',
        ' if fd is not None:os.close(fd)\n if credential is not None:credential.close()\n protected.clear()')
    result = {'cloud-session.py': controller, 'cloud-helpers.py': helpers,
              'private-sql-helper.py': private_helper}
    for name, text in result.items():
        compile(text, name, 'exec')  # Syntax check only; never execute the controller.
    return result


# CN-GCP-SUBNET-API-RECOVERY-001. These adapters never acquire credentials.
SUBNET_URL = ('https://compute.googleapis.com/compute/v1/projects/' + PROJECT
              + '/regions/us-east1/subnetworks/default')
SUBNET_ID = '985145031189724551'
RETAINED_SERVICE_IDS = {'cinenest-runtime': '111620249984773009308',
                        'cinenest-deployer': '102296079302357771326'}


def _compute_link(value):
    if not isinstance(value, str):
        return None
    return value.replace('https://www.googleapis.com/compute/v1/', 'https://compute.googleapis.com/compute/v1/', 1)


def _subnet_snapshot(value):
    import copy
    if (not isinstance(value, dict) or value.get('id') != SUBNET_ID
            or _compute_link(value.get('selfLink')) != SUBNET_URL or value.get('name') != 'default'
            or _compute_link(value.get('region')) != SUBNET_URL.rsplit('/subnetworks/', 1)[0]
            or _compute_link(value.get('network')) != SUBNET_URL.split('/regions/')[0] + '/global/networks/default'
            or value.get('ipCidrRange') != '10.142.0.0/20'
            or type(value.get('privateIpGoogleAccess')) is not bool
            or not isinstance(value.get('fingerprint'), str) or not value['fingerprint']):
        raise ValueError('Unexpected approved subnet identity/state')
    return copy.deepcopy(value)


def _subnet_semantics(value):
    return {k: v for k, v in value.items() if k not in ('privateIpGoogleAccess', 'fingerprint')}


class PrivateGoogleAccessGuard:
    """Journal before POST; no automatic replay of an uncertain mutation.

    setPrivateIpGoogleAccess has no fingerprint precondition. Double observation
    detects drift but is not atomic exclusion against other network operators.
    The caller must hold the approved maintenance/control boundary. Any unknown
    outcome or changed snapshot blocks further changes until reconciled.
    """
    def __init__(self, api, wait_operation, journal, persist):
        self.api, self.wait = api, wait_operation
        self.journal, self.persist = journal, persist

    def _read(self):
        return _subnet_snapshot(self.api('GET', SUBNET_URL))

    def _transition(self, before, desired, label):
        import uuid
        # Exact second observation immediately before the request. No PATCH.
        if self._read() != before:
            raise RuntimeError('Subnet drift before mutation')
        entry = {'before': before, 'desired': desired,
                 'requestId': str(uuid.uuid4()), 'phase': 'UNCERTAIN'}
        self.journal[label] = entry
        self.persist()  # durable write must succeed before mutation
        url = SUBNET_URL + '/setPrivateIpGoogleAccess?requestId=' + entry['requestId']
        op = self.api('POST', url, {'privateIpGoogleAccess': desired})
        expected_prefix = SUBNET_URL.rsplit('/subnetworks/', 1)[0] + '/operations/'
        if (not isinstance(op, dict) or not (_compute_link(op.get('selfLink')) or '').startswith(expected_prefix)
                or _compute_link(op.get('targetLink')) != SUBNET_URL or str(op.get('targetId')) != SUBNET_ID):
            raise RuntimeError('Unexpected subnet operation identity')
        entry.update(operation=op, phase='SUBMITTED'); self.persist()
        done = self.wait(op)
        if (done.get('selfLink') != op['selfLink'] or _compute_link(done.get('targetLink')) != SUBNET_URL
                or str(done.get('targetId')) != SUBNET_ID or done.get('status') != 'DONE'
                or done.get('error') or done.get('httpErrorStatusCode')):
            raise RuntimeError('Subnet operation did not complete successfully')
        after = self._read()
        if after['privateIpGoogleAccess'] is not desired or _subnet_semantics(before) != _subnet_semantics(after):
            raise RuntimeError('Subnet drift after mutation')
        entry.update(after=after, phase='VERIFIED'); self.persist()
        return after

    def enable(self, required):
        if type(required) is not bool:
            raise ValueError('Explicit path requirement required')
        current = self._read()
        if 'enable' in self.journal:
            entry = self.journal['enable']
            if (entry.get('phase') != 'VERIFIED' or current != entry.get('after')
                    or entry.get('desired') is not True):
                raise RuntimeError('Uncertain/replaced subnet change; reconcile first')
            return current
        if not required or current['privateIpGoogleAccess']:
            return current  # pre-existing true is never attributed to this task
        return self._transition(current, True, 'enable')

    def rollback(self):
        entry = self.journal.get('enable')
        if not entry:
            return self._read()  # no task-owned mutation
        current = self._read()
        if 'rollback' in self.journal:
            undo = self.journal['rollback']
            if undo.get('phase') == 'VERIFIED' and current == undo.get('after'):
                return current
            raise RuntimeError('Uncertain rollback; reconcile first')
        if (entry.get('phase') != 'VERIFIED' or entry.get('before', {}).get('privateIpGoogleAccess') is not False
                or entry.get('desired') is not True or current != entry.get('after')):
            raise RuntimeError('Subnet changed; preserve concurrent state')
        return self._transition(current, False, 'rollback')


def qualification_role_baseline(roles, now):
    """Read-only admission for original or precisely retained NOLOGIN states."""
    import datetime
    if not isinstance(now, datetime.datetime) or now.tzinfo is None:
        raise ValueError('Authoritative aware observation time required')
    names = {'cinenest_qualification_runtime', 'cinenest_qualification_migrator'}
    if not isinstance(roles, list) or len(roles) != 2 or {r['name'] for r in roles} != names:
        raise ValueError('Exact qualification roles required')
    for role in roles:
        if set(role) != {'name', 'login', 'superuser', 'createdb', 'createrole', 'replication', 'bypassrls', 'connectionLimit', 'validUntil'}:
            raise ValueError('Unexpected role fields')
        if any(role[k] is not False for k in ('login', 'superuser', 'createdb', 'createrole', 'replication', 'bypassrls')):
            raise ValueError('NOLOGIN restricted roles required')
    pairs = {r['name']: r for r in roles}
    runtime, migrator = (pairs['cinenest_qualification_' + name] for name in ('runtime', 'migrator'))
    if all(type(r['connectionLimit']) is int and r['connectionLimit'] == -1 and r['validUntil'] is None for r in roles):
        return 'ORIGINAL_NOLOGIN'
    if (type(runtime['connectionLimit']) is not int or runtime['connectionLimit'] != 6
            or type(migrator['connectionLimit']) is not int or migrator['connectionLimit'] != 2
            or runtime['validUntil'] is not None or not isinstance(migrator['validUntil'], str)):
        raise ValueError('Unexpected retained role limits')
    expiry = datetime.datetime.fromisoformat(migrator['validUntil'].replace('Z', '+00:00'))
    if expiry.tzinfo is None or expiry > now:
        raise ValueError('Retained migrator validity must have expired')
    return 'RETAINED_REVOKED_NOLOGIN'


def approved_retained_policy_pins(raw, expected_sha256):
    if (not isinstance(raw, bytes) or len(raw) > 16384
            or not re.fullmatch(r'[0-9a-f]{64}', expected_sha256 or '')
            or hashlib.sha256(raw).hexdigest() != expected_sha256):
        raise ValueError('Retained policy pin artifact changed')
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError('Duplicate policy pin')
            result[key] = value
        return result
    pins = json.loads(raw, object_pairs_hook=unique)
    if (not isinstance(pins, dict) or set(pins) != {'repository', *RETAINED_SERVICE_IDS}
            or any(not isinstance(v, str) or not re.fullmatch(r'[0-9a-f]{64}', v) for v in pins.values())):
        raise ValueError('Exact retained resource policy pins required')
    return pins


def retained_qualification_resource(kind, value, policy, approved_policy_sha256):
    """Pins supplied in a separately fingerprinted runtime approval, never self-approved."""
    if (not re.fullmatch(r'[0-9a-f]{64}', approved_policy_sha256 or '')
            or hashlib.sha256(json.dumps(policy, sort_keys=True, separators=(',', ':')).encode()).hexdigest() != approved_policy_sha256):
        raise ValueError('Retained IAM policy drift or missing approved pin')
    if kind in RETAINED_SERVICE_IDS:
        email = kind + '@' + PROJECT + '.iam.gserviceaccount.com'
        if value.get('email') != email or value.get('uniqueId') != RETAINED_SERVICE_IDS[kind] or value.get('disabled', False) is not False or value.get('projectId') != PROJECT:
            raise ValueError('Retained identity drift')
    elif kind == 'repository':
        if (value.get('name') != 'projects/' + PROJECT + '/locations/us-east1/repositories/cinenest-runtime'
                or value.get('createTime') != '2026-11-24T00:56:40.643736Z'
                or value.get('format') != 'DOCKER' or value.get('mode', 'STANDARD_REPOSITORY') != 'STANDARD_REPOSITORY'
                or value.get('labels', {}).get('cinenest-task') != 'runtime-20261122'):
            raise ValueError('Retained registry drift')
    else:
        raise ValueError('Unapproved retained resource')
    for binding in policy.get('bindings', []):
        if any(x in ('allUsers', 'allAuthenticatedUsers') for x in binding.get('members', [])):
            raise ValueError('Public retained-resource policy')
    return True


def subnet_recovery_artifacts(controller, helpers, private_helper):
    """Correct the exact failed capsule offline; never executes or grants authority."""
    def once(old, new):
        nonlocal controller
        if controller.count(old) != 1:
            raise ValueError('Unexpected retained subnet capsule shape')
        controller = controller.replace(old, new, 1)
    once("assert not v['sql_staging_token_errors'](),v['sql_staging_token_errors']()",
         "assert not v['subnet_recovery_errors'](),v['subnet_recovery_errors']()")
    once("qualified=[r['values'] for r in rows[1]['rows'] if r['values'][0]['value'].startswith('cinenest_qualification_')];assert len(qualified)==2 and all(all(r[i]['value']=='false' for i in range(1,7)) and r[7]['value']=='-1' and r[8].get('nullValue') for r in qualified)",
         "qualified=[r['values'] for r in rows[1]['rows'] if r['values'][0]['value'].startswith('cinenest_qualification_')];M['qualification_role_baseline']([dict(zip(['name','login','superuser','createdb','createrole','replication','bypassrls','connectionLimit','validUntil'],[r[0]['value']]+[{'false':False,'true':True}.get(r[i].get('value')) for i in range(1,7)]+[int(r[7]['value']),r[8].get('value')])) for r in qualified],datetime.datetime.now(datetime.timezone.utc))")
    old="before=api('GET',subnet_url);state['subnetBefore']=before;save()\n if not before.get('privateIpGoogleAccess'):\n  operation(api('PATCH',subnet_url,{'privateIpGoogleAccess':True,'fingerprint':before['fingerprint']}));state['pgaChanged']=True;state['subnetAfterFingerprint']=api('GET',subnet_url)['fingerprint'];save()"
    once(old,"assert subnet_url==M['SUBNET_URL'];journal=state.setdefault('subnetChange',{});guard=M['PrivateGoogleAccessGuard'](api,operation,journal,save);guard.enable(required=True)")
    old="if state.get('pgaChanged'):\n  def restore():\n   x=api('GET',subnet_url);assert x['fingerprint']==state['subnetAfterFingerprint'],'Concurrent subnet change;preserve for owner review';operation(api('PATCH',subnet_url,{'privateIpGoogleAccess':False,'fingerprint':x['fingerprint']}))\n  clean('private-google-access',restore)"
    once(old,"if state.get('subnetChange'):\n  clean('private-google-access',lambda:M['PrivateGoogleAccessGuard'](api,operation,state['subnetChange'],save).rollback())")
    # A future runtime package must pin current policies. No undeclared reuse.
    once("for name,cmd in [('fresh-disks'", "state['retainedResources']={}\n pin_path=E/'retained-resource-pins.json';assert not pin_path.is_symlink();pins=M['approved_retained_policy_pins'](pin_path.read_bytes(),fresh['publicInputSha256']['retained-resource-pins.json']);state['retainedPolicyPins']=pins\n for kind in pins:\n  url=('https://artifactregistry.googleapis.com/v1/projects/'+P+'/locations/us-east1/repositories/'+repo) if kind=='repository' else ('https://iam.googleapis.com/v1/projects/'+P+'/serviceAccounts/'+kind+'@'+P+'.iam.gserviceaccount.com')\n  obj=api('GET',url);policy=iam(url)\n  M['retained_qualification_resource'](kind,obj,policy,pins[kind]);state['retainedResources'][kind]={'url':url,'object':obj,'policy':policy};save()\n for name,cmd in [('fresh-disks'")
    once("[VM+'-boot',service,'cinenest-connectivity-migration','cinenest-connectivity-probe',repo,", "[VM+'-boot',service,'cinenest-connectivity-migration','cinenest-connectivity-probe',")
    once("if name=='fresh-sas':assert not any(x['email'].split('@')[0] in ['cinenest-runtime','cinenest-deployer','cinenest-connectivity-probe','cinenest-migrator'] for x in items)", "if name=='fresh-sas':assert not any(x['email'].split('@')[0] in ['cinenest-connectivity-probe','cinenest-migrator'] for x in items)")
    once("for name in ['cinenest-runtime','cinenest-deployer','cinenest-connectivity-probe','cinenest-migrator']:\n  _,out,_=gc(", "for name in ['cinenest-runtime','cinenest-deployer','cinenest-connectivity-probe','cinenest-migrator']:\n  if name in state['retainedResources']:\n   item=state['retainedResources'][name];M['retained_qualification_resource'](name,api('GET',item['url']),iam(item['url']),pins_for_runtime()[name]);continue\n  _,out,_=gc(")
    # Keep pins in the protected exact runtime input, not arbitrary caller data.
    once("def prepare_cloud_resources():", "def pins_for_runtime():\n return dict(state['retainedPolicyPins'])\n\ndef prepare_cloud_resources():")
    old="gc('create-repository',['artifacts','repositories','create',repo,'--repository-format=docker','--location=us-east1','--labels=cinenest-task=runtime-20261122']);state['createdRepo']=True;save();binding("
    once(old,"item=state['retainedResources']['repository'];M['retained_qualification_resource']('repository',api('GET',item['url']),iam(item['url']),pins_for_runtime()['repository']);binding(")
    # Reuse must never be admitted without fingerprinting the policy-pin artifact.
    once("fresh=json.loads((E/'fresh-session-authorization.requested.json').read_text());approved=", "fresh=json.loads((E/'fresh-session-authorization.requested.json').read_text());assert 'retained-resource-pins.json' in fresh['publicInputSha256'];approved=")
    if "api('PATCH',subnet_url" in controller:
        raise ValueError('Invalid subnet PATCH retained')
    for name, text in [('cloud-session.py', controller), ('cloud-helpers.py', helpers), ('private-sql-helper.py', private_helper)]:
        compile(text, name, 'exec')
    return {'cloud-session.py': controller, 'cloud-helpers.py': helpers, 'private-sql-helper.py': private_helper}


def private_known_hosts_option(private_directory, known_hosts):
    """Quote for OpenSSH's config parser, not a shell. Never alter trust files."""
    from pathlib import Path
    import os
    directory, target = map(os.fspath, (private_directory, known_hosts))
    for value in (directory, target):
        if (not isinstance(value, str) or not value or any(ord(c) < 32 or ord(c) == 127 for c in value)
                or any(c in value for c in '\\"$%~*?[]') or not Path(value).is_absolute()
                or str(Path(value)) != value or '..' in Path(value).parts):
            raise ValueError('Unsafe task-private SSH trust path')
    parent, path = Path(directory), Path(target)
    if path != parent / 'known_hosts':
        raise ValueError('Exactly one task-private known_hosts file required')
    for candidate in (path, *path.parents):
        if candidate.is_symlink():
            raise ValueError('Symlink SSH trust path denied')
    if parent.exists() and (not parent.is_dir() or parent.stat().st_mode & 0o077):
        raise ValueError('SSH private directory must be private')
    if path.exists() and (not path.is_file() or path.stat().st_mode & 0o077):
        raise ValueError('SSH trust file must be private')
    return 'UserKnownHostsFile="' + target + '"'


def ssh_readiness_result(code, stdout, stderr):
    """No raw stderr, usernames, tokens, paths or remote output in exceptions."""
    lines = stdout.splitlines() if isinstance(stdout, str) else []
    if type(code) is int and code == 0 and len(lines) >= 3 and lines[0].isdigit() and int(lines[0]) > 0 and all(lines[1:3]):
        return True
    text = stderr.lower() if isinstance(stderr, str) else ''
    reason = ('HOST_KEY_REJECTED' if 'host key verification failed' in text or 'remote host identification has changed' in text
              else 'AUTHENTICATION_DENIED' if 'permission denied' in text
              else 'NON_ROOT_TOOLING_REQUIRED' if code == 0
              else 'TRANSPORT_OR_CLIENT_FAILURE')
    raise RuntimeError('SSH_READY:' + reason)


def ssh_trust_path_artifacts(controller, helpers, private_helper):
    """Exact retained-capsule correction only; compile, never execute cloud code."""
    replacements = [
        ("assert not v['subnet_recovery_errors'](),v['subnet_recovery_errors']()",
         "assert not v['ssh_trust_path_errors'](),v['ssh_trust_path_errors']()"),
        ("'UserKnownHostsFile='+str(keydir/'known_hosts')",
         "M['private_known_hosts_option'](keydir,keydir/'known_hosts')"),
        ("  if code==0 and out.splitlines()[0]!='0':break",
         "  if code==0 and out.splitlines() and out.splitlines()[0]!='0':break"),
        (" assert code==0\n files=[E/'client.tar.gz'",
         " M['ssh_readiness_result'](code,out,err)\n files=[E/'client.tar.gz'")]
    for old, new in replacements:
        if controller.count(old) != 1:
            raise ValueError('Unexpected retained SSH capsule shape')
        controller = controller.replace(old, new, 1)
    for required in ("'-o','IdentitiesOnly=yes'", "'-o','BatchMode=yes'", "'-o','StrictHostKeyChecking=accept-new'",
                     "'-o','GlobalKnownHostsFile=/dev/null'", "ssh=['ssh',*opts,username+'@'+VM]", "['scp',*opts,"):
        if controller.count(required) != 1:
            raise ValueError('SSH/SCP security boundary changed')
    for forbidden in ('StrictHostKeyChecking=no', 'UserKnownHostsFile=/dev/null', 'ssh-keygen -R'):
        if forbidden in controller:
            raise ValueError('SSH trust bypass denied')
    result = {'cloud-session.py': controller, 'cloud-helpers.py': helpers, 'private-sql-helper.py': private_helper}
    for name, text in result.items():
        compile(text, name, 'exec')
    return result

if __name__=='__main__':raise SystemExit(main())
