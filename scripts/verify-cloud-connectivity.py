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


SQL00_REVISION_SHA256 = '07d9b1a1199efe212697f443c2be1fcab8d47d7ce531006463807ee60c8ab884'
SQL00_REVISION_APPROVAL = 'd4de6e3487335c535b5ae965e5c9e42ada66bcbe4d15138a88f7ed4a540ff8d0'
SQL00_REVISION_FILES = dict(SQL_FILES, **{'00-activation-authority-probe.sql.proposed': SQL00_REVISION_SHA256})


def require_sql00_revision_manifest(manifest):
    """Closed, ordered seven-file revision; never alters historical SQL_FILES."""
    if isinstance(manifest, str):
        def unique(pairs):
            result = {}
            for name, value in pairs:
                if name in result:
                    raise ValueError('Duplicate revised SQL input')
                result[name] = value
            return result
        manifest = json.loads(manifest, object_pairs_hook=unique)
    if (not isinstance(manifest, dict) or manifest != SQL00_REVISION_FILES
            or list(manifest) != list(SQL00_REVISION_FILES)):
        raise ValueError('Exact admitted SQL00 revision and unchanged siblings required')
    if hashlib.sha256(SQL00_REVISION_SQL.encode()).hexdigest() != SQL00_REVISION_SHA256:
        raise ValueError('Revised SQL00 source drift')
    return dict(manifest)


def sql00_role_baseline(roles, now):
    """Preliminary role check; SQL00 database time/ACL/authority remains mandatory."""
    import datetime
    if not isinstance(now, datetime.datetime) or now.tzinfo is None:
        raise ValueError('Aware observation time required')
    names = {'cinenest_qualification_runtime', 'cinenest_qualification_migrator'}
    fields = {'name', 'login', 'superuser', 'createdb', 'createrole', 'replication', 'bypassrls', 'connectionLimit', 'validUntil'}
    if not isinstance(roles, list) or len(roles) != 2 or any(not isinstance(r, dict) or set(r) != fields for r in roles) or {r['name'] for r in roles} != names:
        raise ValueError('Exact two role records required')
    for role in roles:
        if any(role[k] is not False for k in ('login', 'superuser', 'createdb', 'createrole', 'replication', 'bypassrls')) or type(role['connectionLimit']) is not int:
            raise ValueError('Restricted NOLOGIN role required')
    if all(r['connectionLimit'] == -1 and r['validUntil'] is None for r in roles):
        return 'ORIGINAL_NOLOGIN'
    for role in roles:
        if role['connectionLimit'] != (6 if role['name'].endswith('_runtime') else 2):
            raise ValueError('Unexpected paired role limits')
        if role['validUntil'] is not None:
            if not isinstance(role['validUntil'], str):
                raise ValueError('Invalid role validity')
            expiry = datetime.datetime.fromisoformat(role['validUntil'].replace('Z', '+00:00'))
            if expiry.tzinfo is None or expiry > now:
                raise ValueError('Inactive finite expired validity required')
    return 'RETAINED_REVOKED_NOLOGIN'


def prepare_sql00_revision_inputs(legacy, destination):
    """Public SQL only. Exclusive versioned output; original archives untouched."""
    legacy, destination = Path(legacy), Path(destination)
    if destination.exists() or destination.is_symlink() or not destination.parent.is_dir():
        raise ValueError('New versioned SQL directory required')
    # Authenticate every historical byte, including the superseded original00.
    if legacy.is_symlink() or sql_input_names(legacy) != set(SQL_FILES):
        raise ValueError('Historical seven-file inventory required')
    data = {}
    for name, digest in SQL_FILES.items():
        path = legacy / name
        if path.is_symlink() or not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            raise ValueError('Historical SQL fingerprint drift')
        data[name] = path.read_bytes()
    data['00-activation-authority-probe.sql.proposed'] = SQL00_REVISION_SQL.encode()
    require_sql00_revision_manifest(SQL00_REVISION_FILES)
    destination.mkdir(mode=0o700)
    created = []
    try:
        for name in SQL00_REVISION_FILES:
            path = destination / name
            fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
            created.append(path)
            with os.fdopen(fd, 'wb') as stream:
                stream.write(data[name])
        with stage_approved_sql(destination, destination.parent, json.dumps(SQL00_REVISION_FILES), SQL00_REVISION_FILES):
            pass
        return dict(SQL00_REVISION_FILES)
    except BaseException:
        for path in reversed(created):
            path.unlink()
        destination.rmdir()
        raise


def sql00_staging_command():
    import shlex
    program = ("import runpy;from pathlib import Path;"
               "m=runpy.run_path('cinenest-runtime/managed-role-contract.py');"
               "m['install_sql00_revision']('.', 'cinenest-runtime', "
               "Path('cinenest-runtime/approved-sql-hashes.json').read_text())")
    return 'python3 -c ' + shlex.quote(program)


def sql00_revision_artifacts(controller, helpers, private_helper):
    """Offline capsule binding only; source/SQL approval adds no cloud session."""
    def once(old, new):
        nonlocal controller
        if controller.count(old) != 1:
            raise ValueError('Unexpected retained SQL00 capsule shape')
        controller = controller.replace(old, new, 1)
    once("assert not v['ssh_trust_path_errors'](),v['ssh_trust_path_errors']()",
         "assert not v['sql00_retained_errors'](),v['sql00_retained_errors']()")
    once("assert fresh['sqlTemplatesSha256']==json.loads((CACHE/'execution-authorization.requested.json').read_text())['sqlTemplatesSha256']",
         "M['require_sql00_revision_manifest'](fresh['sqlTemplatesSha256']);assert fresh['sql00RevisionApprovalSha256']==M['SQL00_REVISION_APPROVAL']")
    once("manifests=json.loads((E/'approved-sql-hashes.json').read_text());assert all(hashlib.sha256((CACHE/n).read_bytes()).hexdigest()==h for n,h in manifests.items())",
         "manifests=M['require_sql00_revision_manifest']((E/'approved-sql-hashes.json').read_text());SQLROOT=E/'sql-inputs';assert not SQLROOT.is_symlink() and SQLROOT.is_dir() and M['sql_input_names'](SQLROOT)==set(manifests);assert all(not (SQLROOT/n).is_symlink() and (SQLROOT/n).is_file() and hashlib.sha256((SQLROOT/n).read_bytes()).hexdigest()==h for n,h in manifests.items())")
    once("M['qualification_role_baseline'](", "M['sql00_role_baseline'](")
    once("[CACHE/n for n in json.loads((E/'approved-sql-hashes.json').read_text())]",
         "[E/'sql-inputs'/n for n in M['require_sql00_revision_manifest']((E/'approved-sql-hashes.json').read_text())]")
    once("M['sql_staging_command']()", "M['sql00_staging_command']()")
    old="files=json.loads((E/'approved-sql-hashes.json').read_text());assert all(hashlib.sha256((E/n).read_bytes()).hexdigest()==h for n,h in files.items())"
    if private_helper.count(old) != 1:
        raise ValueError('Unexpected private SQL manifest boundary')
    private_helper = private_helper.replace(old,
        "contract_path=E/'managed-role-contract.py';assert hashlib.sha256(contract_path.read_bytes()).hexdigest()==(E/'managed-role-source.sha256').read_text().strip();files=runpy.run_path(str(contract_path))['require_sql00_revision_manifest']((E/'approved-sql-hashes.json').read_text());assert all(hashlib.sha256((E/n).read_bytes()).hexdigest()==h for n,h in files.items())", 1)
    if ("assert state.get('authorityProbe')=='PASS'" not in controller
            or helpers.count("result=private_sql('probe' if self.n==1 else 'authority')") != 1
            or "[CACHE/n for n" in controller):
        raise ValueError('Mandatory authority/staging sequence changed')
    if helpers.count("M['AUTHORITY_PROBE_SHA256']") != 1 or helpers.count("M['managed_role_confirmation']") != 2:
        raise ValueError('Unexpected authority confirmation binding')
    helpers = helpers.replace("M['AUTHORITY_PROBE_SHA256']", "M['SQL00_REVISION_SHA256']").replace("M['managed_role_confirmation']", "M['sql00_managed_role_confirmation']")
    result = {'cloud-session.py': controller, 'cloud-helpers.py': helpers, 'private-sql-helper.py': private_helper}
    for name, text in result.items():
        compile(text, name, 'exec')
    return result


SQL00_REVISION_SQL = r'''-- PROPOSED REVISED SQL00. Exact admission required before any Cloud SQL execution.
-- Original fingerprint cd2a892c70da4197cacc61e815419ed7014de0f441606c2cc40b851a78c28c2d remains historical.
-- Controller verifies revoked baseline, authorized bounded delegation and independent revoker.
-- This probe runs only inside that delegation and always rolls back its no-op ALTER checks.
\set ON_ERROR_STOP on
BEGIN;
SET LOCAL lock_timeout='5s';
SET LOCAL statement_timeout='30s';
DO $guard$
DECLARE
 runtime_oid oid; migrator_oid oid; actor_oid oid; manager_oid oid;
 qualification_oid oid; role_count integer;
BEGIN
 IF current_database()<>'postgres' OR current_user<>'cloudsqlsuperuser'
    OR session_user<>'cinenest44@gmail.com' THEN RAISE EXCEPTION 'Wrong execution identity/database'; END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_stat_ssl WHERE pid=pg_backend_pid() AND ssl)
    OR current_setting('password_encryption')<>'scram-sha-256' THEN RAISE EXCEPTION 'Verified TLS and SCRAM required'; END IF;
 SELECT oid INTO actor_oid FROM pg_roles WHERE rolname=session_user AND NOT rolsuper AND NOT rolcreatedb AND NOT rolcreaterole AND NOT rolreplication AND NOT rolbypassrls;
 SELECT oid INTO manager_oid FROM pg_roles WHERE rolname=current_user;
 SELECT oid INTO runtime_oid FROM pg_roles WHERE rolname='cinenest_qualification_runtime';
 SELECT oid INTO migrator_oid FROM pg_roles WHERE rolname='cinenest_qualification_migrator';
 IF actor_oid IS NULL OR manager_oid IS NULL OR runtime_oid IS NULL OR migrator_oid IS NULL
 THEN RAISE EXCEPTION 'Missing or escalated identity/role'; END IF;
 SELECT count(*) INTO role_count FROM pg_roles WHERE starts_with(rolname,'cinenest_qualification_');
 IF role_count<>2 OR (SELECT count(*) FROM pg_roles WHERE oid IN(runtime_oid,migrator_oid)
   AND NOT rolcanlogin AND NOT rolsuper AND NOT rolcreatedb AND NOT rolcreaterole
   AND NOT rolreplication AND NOT rolbypassrls)<>2
 THEN RAISE EXCEPTION 'Unexpected active/elevated qualification role'; END IF;
 -- Accept complete pairs only: original baseline, or configured inactive retry state.
 -- NULL validity represents not-yet-activated preparation. Finite past validity is
 -- preserved revoked history. Future/infinite validity is never admitted here.
 IF NOT (
   (SELECT count(*) FROM pg_roles WHERE oid IN(runtime_oid,migrator_oid) AND rolconnlimit=-1 AND rolvaliduntil IS NULL)=2
   OR
   (SELECT count(*) FROM pg_roles WHERE oid IN(runtime_oid,migrator_oid)
      AND ((oid=runtime_oid AND rolconnlimit=6) OR (oid=migrator_oid AND rolconnlimit=2))
      AND (rolvaliduntil IS NULL OR (isfinite(rolvaliduntil) AND rolvaliduntil<=clock_timestamp())))=2
 ) THEN RAISE EXCEPTION 'Unexpected role baseline;no blind rotation'; END IF;
 -- No role inheritance/delegation from either application identity. Only the
 -- evidenced provider/creator administration edges into them may remain.
 IF EXISTS(SELECT 1 FROM pg_auth_members WHERE member IN(runtime_oid,migrator_oid))
    OR (SELECT count(*) FROM pg_auth_members WHERE roleid IN(runtime_oid,migrator_oid))<>2
    OR EXISTS(SELECT 1 FROM pg_auth_members m JOIN pg_roles g ON g.oid=m.grantor
      WHERE m.roleid IN(runtime_oid,migrator_oid) AND NOT
       (m.member=manager_oid AND NOT m.inherit_option AND NOT m.set_option AND
        g.rolname IN ('cloudsqlsuperuser','cloudsqladmin')))
 THEN RAISE EXCEPTION 'Unexpected qualification role membership'; END IF;
 IF NOT pg_has_role(current_user,'cinenest_qualification_runtime','MEMBER WITH ADMIN OPTION')
    OR NOT pg_has_role(current_user,'cinenest_qualification_migrator','MEMBER WITH ADMIN OPTION')
 THEN RAISE EXCEPTION 'Per-role administration authority unavailable'; END IF;
 SELECT oid INTO qualification_oid FROM pg_database WHERE datname='cinenest_connectivity_qualification'
   AND datdba=migrator_oid AND datallowconn AND NOT datistemplate;
 IF qualification_oid IS NULL OR EXISTS(SELECT 1 FROM pg_database WHERE datdba IN(runtime_oid,migrator_oid) AND oid<>qualification_oid)
 THEN RAISE EXCEPTION 'Unexpected qualification database ownership/state'; END IF;
 IF NOT has_database_privilege(runtime_oid,qualification_oid,'CONNECT')
    OR has_database_privilege(runtime_oid,qualification_oid,'CREATE')
    OR has_database_privilege(runtime_oid,qualification_oid,'TEMP')
    OR NOT has_database_privilege(actor_oid,qualification_oid,'CONNECT')
 THEN RAISE EXCEPTION 'Unexpected qualification database privileges'; END IF;
 IF EXISTS(SELECT 1 FROM pg_database d CROSS JOIN LATERAL aclexplode(COALESCE(d.datacl,acldefault('d',d.datdba))) a
    WHERE (d.oid=qualification_oid AND
      (a.grantee NOT IN(runtime_oid,migrator_oid,actor_oid) OR a.grantor<>migrator_oid
       OR (a.grantee IN(runtime_oid,actor_oid) AND (a.privilege_type<>'CONNECT' OR a.is_grantable))))
     OR (d.oid<>qualification_oid AND a.grantee IN(runtime_oid,migrator_oid)))
 THEN RAISE EXCEPTION 'Unexpected explicit database grant'; END IF;
END;
$guard$;
-- Real effective ALTER capability; neither settings nor history are committed.
ALTER ROLE cinenest_qualification_runtime NOLOGIN;
ALTER ROLE cinenest_qualification_migrator NOLOGIN;
ROLLBACK;
'''

def install_sql00_revision(source, destination, manifest):
    """Install the complete verified bundle before any credential or authority.

    Only the frozen source-owned SQL00_REVISION_FILES contract is accepted. No overwrite,
    extension discovery for admission, or partial output is permitted.
    """
    require_sql00_revision_manifest(manifest)
    destination = Path(destination)
    if destination.is_symlink() or not destination.is_dir():
        raise ValueError('Private SQL destination required')
    if stat.S_IMODE(destination.stat().st_mode) != 0o700 or sql_input_names(destination):
        raise ValueError('Empty private SQL destination required')
    created = []
    try:
        with stage_approved_sql(source, destination.parent, manifest, SQL00_REVISION_FILES) as (staged, names):
            for name in names:
                # Hard linking verified private files is atomic and refuses overwrite.
                os.link(staged / name, destination / name, follow_symlinks=False)
                created.append(destination / name)
            if sql_input_names(destination) != set(SQL00_REVISION_FILES):
                raise ValueError('Installed SQL inventory mismatch')
            for name, expected in SQL00_REVISION_FILES.items():
                if hashlib.sha256((destination / name).read_bytes()).hexdigest() != expected:
                    raise ValueError('Installed SQL fingerprint mismatch')
        return {'status': 'VERIFIED_PUBLIC_SQL_STAGING', 'orderedFiles': list(SQL00_REVISION_FILES)}
    except BaseException:
        for path in reversed(created):
            path.unlink()
        raise




def sql00_managed_role_confirmation(user,sql,context,phase,now=None):
    """Validate actual fresh SQL facts plus scoped API identity;metadata alone never passes.
    Supplied snapshots are not execution evidence. Callers must use a new authenticated
    connection after the completed mutation,keep the independent revoker armed,and run
    exact admitted probe00 revision before any credential or activation action.
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
            if context.get('revokerArmed') is not True or context.get('probeSha256')!=SQL00_REVISION_SHA256 or context.get('probePassed') is not True:raise ValueError()
            if sql.get('currentUser')!=MANAGED_ROLE or any(sql.get(x) is not True for x in ['member','canSet','runtimeAdmin','migratorAdmin']):raise ValueError()
        else:
            if roles is not None and roles!=[]:raise ValueError()
            if sql.get('currentUser')!=MANAGED_PRINCIPAL or sql.get('restricted') is not True or sql['edges'] or any(sql.get(x) is not False for x in ['member','canSet','runtimeAdmin','migratorAdmin']):raise ValueError()
    except (ValueError,TypeError,KeyError,AttributeError,OverflowError):errors.append('Fresh scoped PostgreSQL authority not confirmed')
    return {'status':'DENIED' if errors else 'CONFIRMED','phase':phase,'metadata':'UNKNOWN_OPTIONAL_FIELD' if roles is None else 'OBSERVED','errors':errors,'productionAuthorization':'NONE'}



# Owner-approved delegated-actor correction. Historical revisions remain immutable.
SQL00_ACTOR_SHA256 = '4bdfcf360c8c93b7569b83319c623f26425c7c41ce6ba7268f33efe9dd2c624c'
SQL00_ACTOR_APPROVAL = '7baffd9c976c457cbe6db25ab41495ef81dc478b2dc88ce28f3a1614ea2bd902'
SQL00_ACTOR_FILES = dict(SQL_FILES, **{'00-activation-authority-probe.sql.proposed': SQL00_ACTOR_SHA256})

def require_sql00_actor_manifest(manifest):
    """Closed, ordered seven-file revision; never alters historical SQL_FILES."""
    if isinstance(manifest, str):
        def unique(pairs):
            result = {}
            for name, value in pairs:
                if name in result:
                    raise ValueError('Duplicate revised SQL input')
                result[name] = value
            return result
        manifest = json.loads(manifest, object_pairs_hook=unique)
    if (not isinstance(manifest, dict) or manifest != SQL00_ACTOR_FILES
            or list(manifest) != list(SQL00_ACTOR_FILES)):
        raise ValueError('Exact admitted SQL00 revision and unchanged siblings required')
    if hashlib.sha256(SQL00_ACTOR_SQL.encode()).hexdigest() != SQL00_ACTOR_SHA256:
        raise ValueError('Revised SQL00 source drift')
    return dict(manifest)


def prepare_sql00_actor_inputs(legacy, destination):
    """Public SQL only. Exclusive versioned output; original archives untouched."""
    legacy, destination = Path(legacy), Path(destination)
    if destination.exists() or destination.is_symlink() or not destination.parent.is_dir():
        raise ValueError('New versioned SQL directory required')
    # Authenticate every historical byte, including the superseded original00.
    if legacy.is_symlink() or sql_input_names(legacy) != set(SQL_FILES):
        raise ValueError('Historical seven-file inventory required')
    data = {}
    for name, digest in SQL_FILES.items():
        path = legacy / name
        if path.is_symlink() or not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            raise ValueError('Historical SQL fingerprint drift')
        data[name] = path.read_bytes()
    data['00-activation-authority-probe.sql.proposed'] = SQL00_ACTOR_SQL.encode()
    require_sql00_actor_manifest(SQL00_ACTOR_FILES)
    destination.mkdir(mode=0o700)
    created = []
    try:
        for name in SQL00_ACTOR_FILES:
            path = destination / name
            fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
            created.append(path)
            with os.fdopen(fd, 'wb') as stream:
                stream.write(data[name])
        with stage_approved_sql(destination, destination.parent, json.dumps(SQL00_ACTOR_FILES), SQL00_ACTOR_FILES):
            pass
        return dict(SQL00_ACTOR_FILES)
    except BaseException:
        for path in reversed(created):
            path.unlink()
        destination.rmdir()
        raise


def install_sql00_actor(source, destination, manifest):
    """Install the complete verified bundle before any credential or authority.

    Only the frozen source-owned SQL00_ACTOR_FILES contract is accepted. No overwrite,
    extension discovery for admission, or partial output is permitted.
    """
    require_sql00_actor_manifest(manifest)
    destination = Path(destination)
    if destination.is_symlink() or not destination.is_dir():
        raise ValueError('Private SQL destination required')
    if stat.S_IMODE(destination.stat().st_mode) != 0o700 or sql_input_names(destination):
        raise ValueError('Empty private SQL destination required')
    created = []
    try:
        with stage_approved_sql(source, destination.parent, manifest, SQL00_ACTOR_FILES) as (staged, names):
            for name in names:
                # Hard linking verified private files is atomic and refuses overwrite.
                os.link(staged / name, destination / name, follow_symlinks=False)
                created.append(destination / name)
            if sql_input_names(destination) != set(SQL00_ACTOR_FILES):
                raise ValueError('Installed SQL inventory mismatch')
            for name, expected in SQL00_ACTOR_FILES.items():
                if hashlib.sha256((destination / name).read_bytes()).hexdigest() != expected:
                    raise ValueError('Installed SQL fingerprint mismatch')
        return {'status': 'VERIFIED_PUBLIC_SQL_STAGING', 'orderedFiles': list(SQL00_ACTOR_FILES)}
    except BaseException:
        for path in reversed(created):
            path.unlink()
        raise


def sql00_actor_staging_command():
    import shlex
    program = ("import runpy;from pathlib import Path;"
               "m=runpy.run_path('cinenest-runtime/managed-role-contract.py');"
               "m['install_sql00_actor']('.', 'cinenest-runtime', "
               "Path('cinenest-runtime/approved-sql-hashes.json').read_text())")
    return 'python3 -c ' + shlex.quote(program)


def sql00_actor_managed_role_confirmation(user,sql,context,phase,now=None):
    """Validate actual fresh SQL facts plus scoped API identity;metadata alone never passes.
    Supplied snapshots are not execution evidence. Callers must use a new authenticated
    connection after the completed mutation,keep the independent revoker armed,and run
    exact admitted probe00 revision before any credential or activation action.
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
        sql00_actor_flags(sql['actorFlags'],phase)
        if sql.get('restricted') is not (not any(sql['actorFlags'].values())):raise ValueError()
        if phase=='DELEGATED':
            if roles is not None and roles!=[MANAGED_ROLE]:raise ValueError()
            if context.get('revokerArmed') is not True or context.get('probeSha256')!=SQL00_ACTOR_SHA256 or context.get('probePassed') is not True:raise ValueError()
            if sql.get('currentUser')!=MANAGED_ROLE or any(sql.get(x) is not True for x in ['member','canSet','runtimeAdmin','migratorAdmin']):raise ValueError()
        else:
            if roles is not None and roles!=[]:raise ValueError()
            if sql.get('currentUser')!=MANAGED_PRINCIPAL or sql.get('restricted') is not True or sql['edges'] or any(sql.get(x) is not False for x in ['member','canSet','runtimeAdmin','migratorAdmin']):raise ValueError()
    except (ValueError,TypeError,KeyError,AttributeError,OverflowError):errors.append('Fresh scoped PostgreSQL authority not confirmed')
    return {'status':'DENIED' if errors else 'CONFIRMED','phase':phase,'metadata':'UNKNOWN_OPTIONAL_FIELD' if roles is None else 'OBSERVED','errors':errors,'productionAuthorization':'NONE'}


def sql00_actor_flags(flags, phase):
    """Phase-specific actor attributes; never a substitute for scoped authority proof."""
    fields = {'superuser', 'createdb', 'createrole', 'replication', 'bypassrls'}
    if (phase not in ('DELEGATED', 'REVOKED') or not isinstance(flags, dict)
            or set(flags) != fields or any(type(value) is not bool for value in flags.values())
            or any(flags[key] for key in ('superuser', 'replication', 'bypassrls'))
            or (phase == 'REVOKED' and any(flags.values()))):
        raise ValueError('Unsafe,unknown or unrevoked actor flags')
    return 'BOUNDED_DELEGATED_ACTOR' if phase == 'DELEGATED' else 'RESTRICTED_REVOKED_ACTOR'


def sql00_actor_role_query(nonce):
    """Read only exact current actor attributes;no password or credential catalogs."""
    query = managed_role_query(nonce)
    needle = "      'edges',COALESCE("
    if query.count(needle) != 1:
        raise ValueError('Unexpected authority query boundary')
    fields = "      'actorFlags',(SELECT json_build_object('superuser',rolsuper,'createdb',rolcreatedb,'createrole',rolcreaterole,'replication',rolreplication,'bypassrls',rolbypassrls) FROM pg_roles WHERE rolname=session_user),\n"
    return query.replace(needle, fields + needle, 1)


def require_sql00_actor_pregrant(sql, context, now=None):
    """Fresh read-only private proof before delegation;not a completed-mutation claim."""
    try:
        current = now or datetime.now(timezone.utc)
        nonce = context['nonce']
        if (str(uuid.UUID(nonce)) != nonce or sql.get('nonce') != nonce
                or context.get('project') != MANAGED_PROJECT
                or context.get('instance') != MANAGED_INSTANCE
                or context.get('principal') != MANAGED_PRINCIPAL
                or context.get('sslmode') != 'verify-full'
                or context.get('host') != 'ddc7a2d85eb1.18o4rx1o80d5g.us-east1.sql.goog.'
                or context.get('hostaddr') != '10.40.32.2'
                or context.get('caSha256') != 'b9c1735f6619bc814020948a0da2e8970efa76f7d885fd28dbe1c21a85193f46'
                or sql.get('currentUser') != MANAGED_PRINCIPAL
                or sql.get('sessionUser') != MANAGED_PRINCIPAL
                or sql.get('database') != 'postgres' or sql.get('tls') is not True
                or sql.get('restricted') is not True or sql.get('edges') != []
                or any(sql.get(k) is not False for k in ('member','canSet','runtimeAdmin','migratorAdmin'))):
            raise ValueError()
        observed = datetime.fromisoformat(sql['observedAt'])
        if current.tzinfo is None or observed.tzinfo is None or not 0 <= (current-observed).total_seconds() <= 10:
            raise ValueError()
        sql00_actor_flags(sql['actorFlags'], 'REVOKED')
    except (ValueError,TypeError,KeyError,AttributeError,OverflowError):
        raise ValueError('Fresh private restricted actor proof required before grant') from None
    return 'RESTRICTED_BEFORE_GRANT'


def sql00_actor_artifacts(controller, helpers, private_helper):
    """Offline version binding only. Never launches or authorizes cloud execution."""
    def replace(text, old, new, count):
        if text.count(old) != count:
            raise ValueError('Unexpected retained actor-correction boundary')
        return text.replace(old, new)
    controller = replace(controller, "v['sql00_retained_errors']", "v['sql00_actor_errors']", 2)
    controller = replace(controller, "M['require_sql00_revision_manifest']", "M['require_sql00_actor_manifest']", 3)
    controller = replace(controller, "fresh['sql00RevisionApprovalSha256']", "fresh['sql00ActorApprovalSha256']", 1)
    controller = replace(controller, "M['SQL00_REVISION_APPROVAL']", "M['SQL00_ACTOR_APPROVAL']", 1)
    controller = replace(controller, "M['sql00_staging_command']", "M['sql00_actor_staging_command']", 1)
    boundary = " assert all(v['value']=='false' for v in rows[3]['rows'][0]['values'])\n"
    actor_check = " actor=[r['values'] for r in rows[1]['rows'] if r['values'][0].get('value')==USER];assert len(actor)==1;M['sql00_actor_flags']({k:{'true':True,'false':False}.get(actor[0][i].get('value')) for k,i in [('superuser',2),('createdb',3),('createrole',4),('replication',5),('bypassrls',6)]},'REVOKED')\n"
    controller = replace(controller, boundary, actor_check + boundary, 1)
    boundary = "  assert state['bursts']<3 and managed_roles()==[]\n"
    pregrant = "  before=private_sql('revocation');M['require_sql00_actor_pregrant'](before['authorityFacts'],{'nonce':before['_requestNonce'],'project':P,'instance':'cine-nest-2026','principal':USER,'sslmode':'verify-full','host':HOST,'hostaddr':'10.40.32.2','caSha256':'b9c1735f6619bc814020948a0da2e8970efa76f7d885fd28dbe1c21a85193f46'})\n"
    helpers = replace(helpers, boundary, boundary + pregrant, 1)
    helpers = replace(helpers, "M['SQL00_REVISION_SHA256']", "M['SQL00_ACTOR_SHA256']", 1)
    helpers = replace(helpers, "M['sql00_managed_role_confirmation']", "M['sql00_actor_managed_role_confirmation']", 2)
    private_helper = replace(private_helper, "['require_sql00_revision_manifest']", "['require_sql00_actor_manifest']", 1)
    private_helper = replace(private_helper, "contract['managed_role_query']", "contract['sql00_actor_role_query']", 2)
    if ("assert state.get('authorityProbe')=='PASS'" not in controller
            or helpers.count("result=private_sql('probe' if self.n==1 else 'authority')") != 1):
        raise ValueError('Mandatory probe-before-credentials boundary changed')
    result = {'cloud-session.py': controller, 'cloud-helpers.py': helpers, 'private-sql-helper.py': private_helper}
    for name, text in result.items():
        compile(text, name, 'exec')
    return result


SQL00_ACTOR_SQL = r'''-- PROPOSED REVISED SQL00. Exact admission required before any Cloud SQL execution.
-- Original fingerprint cd2a892c70da4197cacc61e815419ed7014de0f441606c2cc40b851a78c28c2d remains historical.
-- Controller verifies revoked baseline, authorized bounded delegation and independent revoker.
-- This probe runs only inside that delegation and always rolls back its no-op ALTER checks.
\set ON_ERROR_STOP on
BEGIN;
SET LOCAL lock_timeout='5s';
SET LOCAL statement_timeout='30s';
DO $guard$
DECLARE
 runtime_oid oid; migrator_oid oid; actor_oid oid; manager_oid oid;
 qualification_oid oid; role_count integer;
BEGIN
 IF current_database()<>'postgres' OR current_user<>'cloudsqlsuperuser'
    OR session_user<>'cinenest44@gmail.com' THEN RAISE EXCEPTION 'Wrong execution identity/database'; END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_stat_ssl WHERE pid=pg_backend_pid() AND ssl)
    OR current_setting('password_encryption')<>'scram-sha-256' THEN RAISE EXCEPTION 'Verified TLS and SCRAM required'; END IF;
 -- Proposed phase-aware administrator check. Exact scoped delegation is required;
 -- CREATEDB/CREATEROLE may be provider-assigned only for this temporary actor.
 -- The controller must prove both absent before delegation and after revocation.
 RAISE NOTICE 'SQL00_ACTOR_FLAGS:%',(SELECT json_build_object(
   'currentUser',current_user,'sessionUser',session_user,'observedAt',clock_timestamp(),
   'superuser',rolsuper,'createdb',rolcreatedb,'createrole',rolcreaterole,
   'replication',rolreplication,'bypassrls',rolbypassrls)
   FROM pg_roles WHERE rolname=session_user);
 IF NOT pg_has_role(session_user,'cloudsqlsuperuser','MEMBER')
    OR NOT pg_has_role(session_user,'cloudsqlsuperuser','SET')
 THEN RAISE EXCEPTION 'Exact delegated administrator membership required'; END IF;
 SELECT oid INTO actor_oid FROM pg_roles WHERE rolname=session_user
   AND NOT rolsuper AND NOT rolreplication AND NOT rolbypassrls;
 SELECT oid INTO manager_oid FROM pg_roles WHERE rolname=current_user;
 SELECT oid INTO runtime_oid FROM pg_roles WHERE rolname='cinenest_qualification_runtime';
 SELECT oid INTO migrator_oid FROM pg_roles WHERE rolname='cinenest_qualification_migrator';
 IF actor_oid IS NULL OR manager_oid IS NULL OR runtime_oid IS NULL OR migrator_oid IS NULL
 THEN RAISE EXCEPTION 'Missing or escalated identity/role'; END IF;
 SELECT count(*) INTO role_count FROM pg_roles WHERE starts_with(rolname,'cinenest_qualification_');
 IF role_count<>2 OR (SELECT count(*) FROM pg_roles WHERE oid IN(runtime_oid,migrator_oid)
   AND NOT rolcanlogin AND NOT rolsuper AND NOT rolcreatedb AND NOT rolcreaterole
   AND NOT rolreplication AND NOT rolbypassrls)<>2
 THEN RAISE EXCEPTION 'Unexpected active/elevated qualification role'; END IF;
 -- Accept complete pairs only: original baseline, or configured inactive retry state.
 -- NULL validity represents not-yet-activated preparation. Finite past validity is
 -- preserved revoked history. Future/infinite validity is never admitted here.
 IF NOT (
   (SELECT count(*) FROM pg_roles WHERE oid IN(runtime_oid,migrator_oid) AND rolconnlimit=-1 AND rolvaliduntil IS NULL)=2
   OR
   (SELECT count(*) FROM pg_roles WHERE oid IN(runtime_oid,migrator_oid)
      AND ((oid=runtime_oid AND rolconnlimit=6) OR (oid=migrator_oid AND rolconnlimit=2))
      AND (rolvaliduntil IS NULL OR (isfinite(rolvaliduntil) AND rolvaliduntil<=clock_timestamp())))=2
 ) THEN RAISE EXCEPTION 'Unexpected role baseline;no blind rotation'; END IF;
 -- No role inheritance/delegation from either application identity. Only the
 -- evidenced provider/creator administration edges into them may remain.
 IF EXISTS(SELECT 1 FROM pg_auth_members WHERE member IN(runtime_oid,migrator_oid))
    OR (SELECT count(*) FROM pg_auth_members WHERE roleid IN(runtime_oid,migrator_oid))<>2
    OR EXISTS(SELECT 1 FROM pg_auth_members m JOIN pg_roles g ON g.oid=m.grantor
      WHERE m.roleid IN(runtime_oid,migrator_oid) AND NOT
       (m.member=manager_oid AND NOT m.inherit_option AND NOT m.set_option AND
        g.rolname IN ('cloudsqlsuperuser','cloudsqladmin')))
 THEN RAISE EXCEPTION 'Unexpected qualification role membership'; END IF;
 IF NOT pg_has_role(current_user,'cinenest_qualification_runtime','MEMBER WITH ADMIN OPTION')
    OR NOT pg_has_role(current_user,'cinenest_qualification_migrator','MEMBER WITH ADMIN OPTION')
 THEN RAISE EXCEPTION 'Per-role administration authority unavailable'; END IF;
 SELECT oid INTO qualification_oid FROM pg_database WHERE datname='cinenest_connectivity_qualification'
   AND datdba=migrator_oid AND datallowconn AND NOT datistemplate;
 IF qualification_oid IS NULL OR EXISTS(SELECT 1 FROM pg_database WHERE datdba IN(runtime_oid,migrator_oid) AND oid<>qualification_oid)
 THEN RAISE EXCEPTION 'Unexpected qualification database ownership/state'; END IF;
 IF NOT has_database_privilege(runtime_oid,qualification_oid,'CONNECT')
    OR has_database_privilege(runtime_oid,qualification_oid,'CREATE')
    OR has_database_privilege(runtime_oid,qualification_oid,'TEMP')
    OR NOT has_database_privilege(actor_oid,qualification_oid,'CONNECT')
 THEN RAISE EXCEPTION 'Unexpected qualification database privileges'; END IF;
 IF EXISTS(SELECT 1 FROM pg_database d CROSS JOIN LATERAL aclexplode(COALESCE(d.datacl,acldefault('d',d.datdba))) a
    WHERE (d.oid=qualification_oid AND
      (a.grantee NOT IN(runtime_oid,migrator_oid,actor_oid) OR a.grantor<>migrator_oid
       OR (a.grantee IN(runtime_oid,actor_oid) AND (a.privilege_type<>'CONNECT' OR a.is_grantable))))
     OR (d.oid<>qualification_oid AND a.grantee IN(runtime_oid,migrator_oid)))
 THEN RAISE EXCEPTION 'Unexpected explicit database grant'; END IF;
END;
$guard$;
-- Real effective ALTER capability; neither settings nor history are committed.
ALTER ROLE cinenest_qualification_runtime NOLOGIN;
ALTER ROLE cinenest_qualification_migrator NOLOGIN;
ROLLBACK;
'''


# Owner-approved source-only execution monitoring and cleanup successor.
import math
MONITORING_REGION = 'us-east1'
MONITORING_PROJECT_NUMBER = '980464067041'
MONITORING_APPROVAL_SHA256 = 'cd196604ac7669987bbc4af53fa666692a15a48d690405e1883d837daf66b093'
MONITORING_PRINCIPAL = 'cinenest-deployer@' + PROJECT + '.iam.gserviceaccount.com'
MONITORING_JOBS = frozenset(('cinenest-connectivity-migration', 'cinenest-connectivity-probe'))
MONITORING_PERMISSIONS = frozenset(('run.jobs.get', 'run.jobs.run', 'run.executions.get',
                                  'run.executions.list', 'run.tasks.get', 'run.tasks.list'))
JOB_CLEANUP_PERMISSIONS = frozenset(('run.jobs.get', 'run.jobs.delete',
                                   'run.executions.get', 'run.executions.cancel'))
CLEANUP_PROJECT_PERMISSIONS = frozenset(('compute.instances.get', 'compute.instances.delete',
    'compute.disks.get', 'compute.disks.delete', 'compute.zoneOperations.get',
    'compute.regionOperations.get', 'compute.globalOperations.get',
    'compute.firewalls.get', 'compute.firewalls.delete', 'secretmanager.secrets.get',
    'secretmanager.secrets.delete', 'secretmanager.versions.disable',
    'iam.serviceAccounts.delete', 'cloudsql.users.update'))
EXECUTION_STATES = frozenset(('CREATED', 'STARTING', 'RUNNING', 'SUCCEEDED',
                             'FAILED', 'CANCELLED', 'UNKNOWN'))


def monitoring_job_resource(name):
    if isinstance(name, str) and name.startswith('projects/' + MONITORING_PROJECT_NUMBER + '/'):
        name = 'projects/' + PROJECT + name[len('projects/' + MONITORING_PROJECT_NUMBER):]
    prefix = 'projects/' + PROJECT + '/locations/' + MONITORING_REGION + '/jobs/'
    if name not in MONITORING_JOBS:
        if not isinstance(name, str) or not name.startswith(prefix) or name[len(prefix):] not in MONITORING_JOBS:
            raise ValueError('Unapproved job resource')
        return name
    return prefix + name


def monitoring_execution_resource(job, name):
    job = monitoring_job_resource(job)
    if isinstance(name, str) and name.startswith('projects/' + MONITORING_PROJECT_NUMBER + '/'):
        name = 'projects/' + PROJECT + name[len('projects/' + MONITORING_PROJECT_NUMBER):]
    if (not isinstance(name, str) or not name.startswith(job + '/executions/')
            or not re.fullmatch(re.escape(job.rsplit('/', 1)[-1]) + r'-[a-z0-9]{5,32}',
                                name[len(job + '/executions/'):])):
        raise ValueError('Execution outside exact approved job')
    return name


def monitoring_http_retryable(error):
    # Retry reads only. Neither this classifier nor a 401 ever authorizes replaying a mutation.
    return (isinstance(error, (TimeoutError, ConnectionError)) or
            isinstance(error, ProtectedRequestFailure) and error.status in (403, 404, 429, 500, 502, 503, 504))


class DeployerCredential:
    """Exact keyless impersonation result; no repr, logging, caching or disk persistence."""
    __slots__ = ('_token', '_expiry', '_clock', '_monotonic_end', 'principal')

    def __init__(self, token, expiry, principal, clock=None):
        self._token = None
        self._clock = clock or (lambda: credential_datetime.datetime.now(credential_datetime.timezone.utc))
        self.principal = principal
        try:
            end = credential_datetime.datetime.fromisoformat(expiry.replace('Z', '+00:00'))
            remaining = (end - self._clock()).total_seconds()
            if (principal != MONITORING_PRINCIPAL or not isinstance(token, str)
                    or not 16 <= len(token) <= 16384 or re.fullmatch(r'[A-Za-z0-9._~+/-]+=*', token) is None
                    or end.tzinfo is None or not 0 < remaining <= 901):
                raise ValueError()
            self._token, self._expiry = token, end
            self._monotonic_end = time.monotonic() + min(remaining, 900)
        except (ValueError, TypeError, AttributeError):
            raise ValueError('Invalid exact deployer credential or authoritative expiry') from None

    def __repr__(self):
        return '<DeployerCredential protected>'

    def use(self, send, required_seconds):
        try:
            if (type(required_seconds) not in (int, float) or not math.isfinite(required_seconds) or required_seconds <= 0
                    or time.monotonic() + required_seconds >= self._monotonic_end or self._token is None
                    or (self._expiry - self._clock()).total_seconds() < required_seconds):
                raise ValueError('Deployer credential unavailable or insufficient lifetime')
            try:
                result = send(self._token)
                if self._token.encode() in json.dumps(result).encode():
                    raise ValueError('Protected result rejected')
                return result
            except ProtectedRequestFailure:
                raise
            except TimeoutError:
                raise TimeoutError('Protected deployer transport timeout') from None
            except ConnectionError:
                raise ConnectionError('Protected deployer transport unavailable') from None
            except Exception:
                raise RuntimeError('Protected deployer request failed;details withheld') from None
        finally:
            self.close()

    def close(self):
        self._token = None


def mint_monitoring_credential(owner_api, clock=None):
    target = 'https://iam.googleapis.com/v1/projects/' + PROJECT + '/serviceAccounts/' + MONITORING_PRINCIPAL
    identity = owner_api('GET', target)
    if (identity.get('email') != MONITORING_PRINCIPAL or identity.get('uniqueId') != RETAINED_SERVICE_IDS['cinenest-deployer']
            or identity.get('projectId') != PROJECT or identity.get('disabled', False)):
        raise ValueError('Unverified deployer identity')
    raw = owner_api('POST', 'https://iamcredentials.googleapis.com/v1/projects/-/serviceAccounts/'
                    + MONITORING_PRINCIPAL + ':generateAccessToken',
                    {'scope': ['https://www.googleapis.com/auth/cloud-platform'], 'lifetime': '900s'})
    try:
        return DeployerCredential(raw['accessToken'], raw['expireTime'], MONITORING_PRINCIPAL, clock)
    finally:
        raw = None


def execution_state(value):
    """Cloud Run v2 observations. Missing/conflicting evidence is never success."""
    if not isinstance(value, dict) or type(value.get('taskCount')) is not int or value['taskCount'] != 1:
        return 'UNKNOWN'
    conditions = value.get('conditions', [])
    if not isinstance(conditions, list) or any(not isinstance(c, dict) for c in conditions):
        return 'UNKNOWN'
    complete = [c for c in conditions if c.get('type') == 'Completed']
    if len(complete) > 1:
        return 'UNKNOWN'
    counts = [value.get(k, 0) for k in ('succeededCount', 'failedCount', 'cancelledCount', 'runningCount')]
    if any(type(n) is not int or n < 0 for n in counts):
        return 'UNKNOWN'
    succeeded, failed, cancelled, running = counts
    if complete:
        state = complete[0].get('state')
        if state == 'CONDITION_SUCCEEDED':
            return 'SUCCEEDED' if value.get('taskCount') == 1 and succeeded == 1 and failed == cancelled == running == 0 else 'UNKNOWN'
        if state == 'CONDITION_FAILED':
            return 'CANCELLED' if cancelled or complete[0].get('reason') in ('Cancelled', 'CANCELLED') else 'FAILED'
        if state not in ('CONDITION_PENDING', 'STATE_UNSPECIFIED'):
            return 'UNKNOWN'
    if cancelled or failed or succeeded:
        return 'UNKNOWN'  # Counters alone cannot establish a terminal result.
    if running == 1:
        return 'RUNNING'
    if any(c.get('type') == 'Started' for c in conditions):
        return 'STARTING'
    return 'CREATED' if value.get('createTime') and value.get('name') else 'UNKNOWN'


class ExecutionMonitoringError(RuntimeError):
    def __init__(self, execution, state):
        self.execution, self.state = execution, state
        super().__init__('Execution monitoring unavailable; no execution retry: ' + str(execution))


class JobExecutionMonitor:
    """One mutation followed by bounded read-only polling with an explicit cleanup reserve."""
    def __init__(self, job, principal, permission_test, cleanup_test, launch, read,
                 deadline, cost_check, record, clock=time.monotonic, sleep=time.sleep, expected_job_uid=None):
        self.job = monitoring_job_resource(job)
        if principal != MONITORING_PRINCIPAL:
            raise ValueError('Wrong monitoring principal')
        if not isinstance(expected_job_uid, str) or not expected_job_uid:
            raise ValueError('Original job UID required')
        self.expected_job_uid = expected_job_uid
        self.principal, self.test, self.cleanup_test = principal, permission_test, cleanup_test
        self.launch, self.read = launch, read
        if type(deadline) not in (int, float) or not math.isfinite(deadline):
            raise ValueError('Finite session deadline required')
        self.deadline, self.cost_check, self.record = deadline, cost_check, record
        self.clock, self.sleep = clock, sleep
        self.execution, self.state, self.attempted = None, 'UNKNOWN', False

    def budget(self, seconds=0):
        if (self.cost_check() is not True or self.clock() + seconds >= self.deadline - 600):
            raise TimeoutError('Monitoring cost/time/cleanup reserve exhausted')

    def _proof(self, proof, principal, required):
        if (not isinstance(proof, dict) or proof.get('principal') != principal
                or proof.get('resource') != self.job or proof.get('jobUid') != self.expected_job_uid or proof.get('transport') != 'KEYLESS_EXACT_REQUEST'
                or not isinstance(proof.get('permissions'), list)):
            raise ValueError('Wrong permission proof principal/resource/transport')
        return required <= set(proof['permissions'])

    def ready(self, maximum_seconds=120):
        if type(maximum_seconds) is not int or not 0 < maximum_seconds <= 120:
            raise ValueError('Readiness bound expansion')
        end = min(self.clock() + maximum_seconds, self.deadline - 840)
        while self.clock() < end:
            self.budget(240)
            try:
                monitor = self._proof(self.test(self.job, sorted(MONITORING_PERMISSIONS)), self.principal, MONITORING_PERMISSIONS)
                cleanup = self._proof(self.cleanup_test(self.job, sorted(JOB_CLEANUP_PERMISSIONS)), PRINCIPAL, JOB_CLEANUP_PERMISSIONS)
                self.budget(240)
                if self.clock() >= end:
                    raise TimeoutError('Permission readiness observation exceeded bound')
                if monitor and cleanup:
                    self.record({'event': 'PERMISSIONS_READY', 'job': self.job, 'principal': self.principal,
                                 'existingExecutionRead': 'NOT_CLAIMED_RESOURCE_PERMISSION_TEST_ONLY'})
                    return
            except Exception as error:
                if not monitoring_http_retryable(error):
                    raise
                self.record({'event': 'READINESS_RETRY', 'httpStatus': getattr(error, 'status', None)})
            self.sleep(min(2, max(0, end - self.clock())))
        raise TimeoutError('Effective monitoring/cleanup permissions not ready; job not launched')

    def execute(self, maximum_seconds=240):
        if self.attempted:
            raise RuntimeError('Execution creation already attempted; no blind replay')
        if type(maximum_seconds) is not int or not 0 < maximum_seconds <= 240:
            raise ValueError('Monitoring bound expansion')
        self.ready()
        self.budget(maximum_seconds)
        self.attempted = True
        self.record({'event': 'CREATE_REQUESTED', 'job': self.job, 'state': 'UNKNOWN'})
        try:
            created = self.launch(self.job)  # Exactly once, including uncertain responses.
            self.execution = monitoring_execution_resource(self.job, created['name'])
        except Exception:
            self.record({'event': 'CREATION_UNCERTAIN', 'job': self.job, 'state': 'UNKNOWN'})
            raise ExecutionMonitoringError(None, 'UNKNOWN') from None
        self.state = 'CREATED'
        self.record({'event': 'EXECUTION_CREATED', 'execution': self.execution, 'state': self.state})
        end = min(self.clock() + maximum_seconds, self.deadline - 600)
        while self.clock() < end:
            self.budget()
            try:
                value = self.read(self.execution)
                self.budget()
                if self.clock() >= end:
                    break
                if monitoring_execution_resource(self.job, value.get('name')) != self.execution:
                    raise ValueError('Wrong execution observation')
                if monitoring_job_resource(value.get('job', self.job)) != self.job:
                    raise ValueError('Conflicting execution parent')
                self.state = execution_state(value)
                self.record({'event': 'EXECUTION_OBSERVED', 'execution': self.execution, 'state': self.state})
                if self.state in ('SUCCEEDED', 'FAILED', 'CANCELLED'):
                    return {'name': self.execution, 'state': self.state, 'observation': value}
            except Exception as error:
                if not monitoring_http_retryable(error):
                    raise
                self.state = 'UNKNOWN'
                self.record({'event': 'STATUS_READ_RETRY', 'execution': self.execution,
                             'state': self.state, 'httpStatus': getattr(error, 'status', None)})
            self.sleep(min(2, max(0, end - self.clock())))
        raise ExecutionMonitoringError(self.execution, 'UNKNOWN')


def cleanup_binding_with_parent_check(binding, get_parent, remove, owned_jobs, record):
    """Absence is resolution only for an exact task-owned deleted parent; retain evidence."""
    url = binding['url']
    if not isinstance(owned_jobs, dict):
        raise ValueError('Original task job UID inventory required')
    allowed = {'https://run.googleapis.com/v1/projects/' + PROJECT + '/locations/' + MONITORING_REGION + '/jobs/' + n: uid
               for n, uid in owned_jobs.items() if n in MONITORING_JOBS and isinstance(uid, str) and uid}
    if url not in allowed:
        return remove(binding)
    parent = get_parent(url)
    if parent is not None and (parent.get('uid') or parent.get('metadata', {}).get('uid')) != allowed[url]:
        raise ValueError('Job parent UID changed; binding removal refused')
    if parent is None:
        record({'event': 'BINDING_PARENT_ALREADY_ABSENT', 'resource': url, 'verification': 'GET_404'})
        return
    try:
        return remove(binding)
    except ProtectedRequestFailure as error:
        record({'event': 'BINDING_REMOVAL_ERROR', 'resource': url, 'httpStatus': getattr(error, 'status', None)})
        if error.status != 404 or get_parent(url) is not None:
            raise
        record({'event': 'BINDING_PARENT_REMOVAL_VERIFIED', 'resource': url, 'originalHttpStatus': 404})


def owned_deletion_convergence(get_resource, expected_id, delete, record, deadline,
                               attached=False, clock=time.monotonic, sleep=time.sleep):
    """Delete once, then observe. Never extends the shared deadline or deletes an attached disk."""
    if not isinstance(expected_id, str) or not expected_id or type(deadline) not in (int, float) or not math.isfinite(deadline):
        raise ValueError('Exact ownership and bounded deadline required')
    end = min(deadline, clock() + 240)
    issued = False
    started = clock()
    slow_recorded = False
    while clock() < end:
        try:
            resource = get_resource()
        except ProtectedRequestFailure as error:
            record({'event': 'DELETE_READ_ERROR', 'httpStatus': getattr(error, 'status', None)})
            if error.status not in (429, 500, 502, 503, 504):
                raise
            sleep(min(2, max(0, end - clock())))
            continue
        if resource is None:
            record({'event': 'OWNED_RESOURCE_ABSENCE_VERIFIED', 'id': expected_id})
            return 'ABSENT_VERIFIED'
        if str(resource.get('id')) != expected_id:
            raise ValueError('Resource ownership changed; deletion refused')
        if attached and resource.get('users'):
            # The parent deletion can still be detaching its auto-delete disk. Never force it.
            record({'event': 'DISK_STILL_ATTACHED_WAIT', 'id': expected_id})
        elif not issued:
            issued = True  # Set before the mutation: uncertain outcomes must not replay.
            try:
                operation = delete()
                if isinstance(operation, dict):
                    record({'event': 'DELETE_ACCEPTED', 'id': expected_id,
                            'operation': operation.get('name'), 'status': operation.get('status')})
                    if operation.get('error'):
                        raise RuntimeError('Delete operation reported an error')
            except (ProtectedRequestFailure, TimeoutError) as error:
                record({'event': 'DELETE_REQUEST_ERROR', 'id': expected_id,
                        'httpStatus': getattr(error, 'status', None), 'timeout': isinstance(error, TimeoutError)})
                if isinstance(error, ProtectedRequestFailure) and error.status not in (404, 429, 500, 502, 503, 504):
                    raise
        if not slow_recorded and clock() - started >= 100:
            slow_recorded = True
            record({'event': 'DELETION_EXCEEDED_ORIGINAL_POLL', 'id': expected_id, 'seconds': 100})
        sleep(min(2, max(0, end - clock())))
    raise TimeoutError('Exact owned resource absence not verified within cleanup deadline')


MONITORING_CAPSULE_BASE_SHA256 = {'cloud-session.py': '69d95eac7a3124a1c7eca20d6c0fa5931fa628ab1eb9dc4c5617c283f1856204', 'cloud-helpers.py': '7e68f0e8a2a903da7a0a5410a5663da0c135ece1198bd6c5c20d8ac21b933349', 'private-sql-helper.py': 'c2a3490d55b53fd5630f85b74978c82ef7cdb0da80882876a21ebbd98d2c0054'}
MONITORING_LEGACY_BINDING_BLOCK = "   try:remove_binding(b)\n   except RuntimeError as e:\n    if 'API 404' not in str(e):raise"
MONITORING_LEGACY_VM_BLOCK = " if state.get('vmTargetId'):\n  def remove_vm():\n   x=api('GET',V,missing=True)\n   if x:assert x['id']==state['vmTargetId'];operation(api('DELETE',V))\n  clean('private-VM-and-auto-delete-disk',remove_vm)\n if state.get('diskId'):\n  def remove_disk():\n   x=api('GET',state['diskUrl'],missing=True)\n   if x:assert x['id']==state['diskId'] and not x.get('users');operation(api('DELETE',state['diskUrl']))\n  clean('boot-disk',remove_disk)\n"


def monitoring_cleanup_artifacts(controller, helpers, private_helper):
    """Transform only the fingerprinted consumed capsule; output remains unapproved/offline."""
    inputs = {'cloud-session.py': controller, 'cloud-helpers.py': helpers,
              'private-sql-helper.py': private_helper}
    if {n: hashlib.sha256(t.encode()).hexdigest() for n, t in inputs.items()} != MONITORING_CAPSULE_BASE_SHA256:
        raise ValueError('Unknown monitoring capsule input; no authority inferred')
    def once(old, new):
        nonlocal controller
        if controller.count(old) != 1:
            raise ValueError('Monitoring capsule boundary changed')
        controller = controller.replace(old, new, 1)
    begin = controller.index('def job(mode,url=None):')
    end = controller.index('\ndef deploy_service():', begin)
    controller = controller[:begin] + MONITORING_JOB_CONTROLLER + controller[end:]
    once("assert not v['sql00_actor_errors'](),v['sql00_actor_errors']()",
         "assert not v['execution_monitoring_errors'](),v['execution_monitoring_errors']()")
    once(" print('PREFLIGHT:PASS_EXACT_SOURCE_CI_TIME_SCOPE_CREDENTIALS_AND_BUDGET',flush=True)",
         " required=sorted(M['CLEANUP_PROJECT_PERMISSIONS']);proof=api('POST','https://cloudresourcemanager.googleapis.com/v1/projects/'+P+':testIamPermissions',{'permissions':required});assert set(required)<=set(proof.get('permissions',[]))\n print('PREFLIGHT:PASS_EXACT_SOURCE_CI_TIME_SCOPE_CREDENTIALS_AND_BUDGET',flush=True)")
    once(MONITORING_LEGACY_BINDING_BLOCK,
         "   M['cleanup_binding_with_parent_check'](b,lambda url:api('GET',url,missing=True),remove_binding,state.get('jobUids',{}),monitor_record)")
    once(MONITORING_LEGACY_VM_BLOCK, MONITORING_OWNED_CLEANUP)
    once(" for name in reversed(state['createdJobs']):clean('job-'+name,lambda name=name:gc('delete-'+name,['run','jobs','delete',name,'--region=us-east1'],90))",
         " for name in reversed(state['createdJobs']):clean('job-'+name,lambda name=name:M['delete_owned_job'](name,state.get('jobUids',{}).get(name),api,monitor_record,deadline-480))")
    # No source/SQL mutation or runtime receipt is generated by this pure function.
    result = dict(inputs, **{'cloud-session.py': controller,
                            'cloud-helpers.py': helpers + MONITORING_HELPERS})
    for name, text in result.items():
        compile(text, name, 'exec')
    return result


MONITORING_HELPERS = r"""

def monitor_record(event):
 state.setdefault('monitoringEvidence',[]).append(event);save()

def deployer_read_api(method,url,body=None):
 # Exact keyless identity,not the CLI's ambient identity. No credential-bearing response is retained.
 prefixes=['https://run.googleapis.com/v2/'+M['monitoring_job_resource'](n) for n in M['MONITORING_JOBS']]
 allowed=False
 for p in prefixes:
  if url==p+':testIamPermissions' and method=='POST' or url==p and method=='GET':allowed=True
  elif url.startswith(p+'/executions/') and method=='GET':M['monitoring_execution_resource'](p.removeprefix('https://run.googleapis.com/v2/'),url.removeprefix('https://run.googleapis.com/v2/'));allowed=True
 if not allowed:raise ValueError('Unapproved deployer read endpoint')
 credential=M['mint_monitoring_credential'](api)
 def send(token):
  req=urllib.request.Request(url,data=None if body is None else json.dumps(body).encode(),headers={'Authorization':'Bearer '+token,'Content-Type':'application/json'},method=method)
  try:
   with urllib.request.urlopen(req,timeout=max(1,min(25,deadline-time.monotonic()-600))) as response:return json.loads(response.read())
  except urllib.error.HTTPError as error:raise M['ProtectedRequestFailure'](error.code) from None
  except urllib.error.URLError:raise ConnectionError('Protected monitoring transport unavailable') from None
 try:return credential.use(send,30)
 finally:credential.close()

def monitoring_permissions(resource,permissions):
 x=deployer_read_api('POST','https://run.googleapis.com/v2/'+resource+':testIamPermissions',{'permissions':permissions})
 job=deployer_read_api('GET','https://run.googleapis.com/v2/'+resource)
 return {'principal':M['MONITORING_PRINCIPAL'],'resource':resource,'jobUid':job.get('uid'),'permissions':x.get('permissions',[]),'transport':'KEYLESS_EXACT_REQUEST'}

def owner_cleanup_permissions(resource,permissions):
 x=api('POST','https://run.googleapis.com/v2/'+resource+':testIamPermissions',{'permissions':permissions})
 job=api('GET','https://run.googleapis.com/v2/'+resource)
 return {'principal':USER,'resource':resource,'jobUid':job.get('uid'),'permissions':x.get('permissions',[]),'transport':'KEYLESS_EXACT_REQUEST'}
"""

MONITORING_JOB_CONTROLLER = r"""def job(mode,url=None):
 artifact=runpy.run_path(str(R/'scripts/verify-cloud-connectivity.py'));doc=artifact['job_document'](mode,digests['diagnostics'],dict.fromkeys(['runtimeUsername','runtimePassword','migrationPassword','databaseCa'],'1'),url);name=doc['metadata']['name'];p=E/(name+'.json');p.write_text(json.dumps(doc,indent=2)+'\n')
 if name not in state['createdJobs']:
  _,created,_=gc('create-'+name,['run','jobs','replace',str(p),'--region=us-east1'],90);state['createdJobs'].append(name);save();uid=json.loads(created)['metadata']['uid'];assert isinstance(uid,str) and uid;state.setdefault('jobUids',{})[name]=uid;save();resource='https://run.googleapis.com/v1/projects/'+P+'/locations/us-east1/jobs/'+name
  for role in ['roles/run.developer','roles/run.invoker']:binding(resource,role,'serviceAccount:'+state['deployer'])
 else:assert mode=='PROBE','Migration must never be blindly rerun'
 state.setdefault('executions',{});state['executions'][name]=state['executions'].get(name,0)+1;attempt=state['executions'][name];save()
 def launch(resource):
  # --async returns the accepted execution without the CLI's immediate GetExecution.
  _,out,_=gc('execute-'+name+'-'+str(attempt),['run','jobs','execute',name,'--region=us-east1','--async','--impersonate-service-account='+state['deployer']],60)
  x=json.loads(out);meta=x.get('metadata',{});labels=meta.get('labels',{});assert str(meta.get('namespace')) in [P,NUM] and labels.get('run.googleapis.com/job')==name
  return {'name':resource+'/executions/'+meta['name']}
 monitor=M['JobExecutionMonitor'](name,state['deployer'],monitoring_permissions,owner_cleanup_permissions,launch,lambda resource:deployer_read_api('GET','https://run.googleapis.com/v2/'+resource),deadline,lambda:time.monotonic()<deadline-600 and json.loads((E/'cost-reconciliation.json').read_text())['estimatedPhaseUsd']<=json.loads((E/'fresh-session-authorization.requested.json').read_text())['budget']['freshMaximumUsd'],monitor_record,expected_job_uid=state['jobUids'][name])
 result=monitor.execute();state.setdefault('executionResults',[]).append({'mode':mode,'name':result['name'],'state':result['state']});save()
 if result['state']!='SUCCEEDED':raise RuntimeError('Qualification execution ended '+result['state'])
 print(mode+':PASS',flush=True)
 if mode=='MIGRATE':state['migration']='PASS_44_V45';save()
 else:state['probe']='PASS_PRIVATE_JDBC_AND_AUTHENTICATED_HTTP';save()
"""

MONITORING_OWNED_CLEANUP = r""" if state.get('vmTargetId'):
  def remove_vm():
   M['owned_deletion_convergence'](lambda:api('GET',V,missing=True),state['vmTargetId'],lambda:api('DELETE',V),monitor_record,deadline-120)
  clean('private-VM-and-auto-delete-disk',remove_vm)
 if state.get('diskId'):
  def remove_disk():
   M['owned_deletion_convergence'](lambda:api('GET',state['diskUrl'],missing=True),state['diskId'],lambda:api('DELETE',state['diskUrl']),monitor_record,deadline-120,attached=True)
  clean('boot-disk',remove_disk)
"""



def delete_owned_job(name, expected_uid, api, record, deadline, clock=time.monotonic, sleep=time.sleep):
    """Conditional Cloud Run v2 deletion, never a name-only deletion of a replacement job."""
    resource = monitoring_job_resource(name)
    if not isinstance(expected_uid, str) or not expected_uid:
        raise ValueError('Original task job UID unavailable; deletion refused')
    url = 'https://run.googleapis.com/v2/' + resource
    issued = False
    end = min(deadline, clock() + 120)
    while clock() < end:
        try:
            value = api('GET', url, missing=True)
        except ProtectedRequestFailure as error:
            record({'event': 'JOB_DELETE_READ_ERROR', 'httpStatus': error.status})
            if error.status not in (429, 500, 502, 503, 504):
                raise
            sleep(min(2, max(0, end-clock())))
            continue
        if value is None:
            record({'event': 'OWNED_JOB_ABSENCE_VERIFIED', 'resource': resource, 'uid': expected_uid})
            return
        if value.get('uid') != expected_uid or not isinstance(value.get('etag'), str) or not value['etag']:
            raise ValueError('Job identity/version unavailable or changed; deletion refused')
        if not issued:
            issued = True
            from urllib.parse import urlencode
            try:
                op = api('DELETE', url + '?' + urlencode({'etag': value['etag']}))
                record({'event': 'JOB_DELETE_ACCEPTED', 'resource': resource, 'operation': op.get('name')})
                if op.get('error'):
                    raise RuntimeError('Job deletion operation failed')
            except (ProtectedRequestFailure, TimeoutError) as error:
                record({'event': 'JOB_DELETE_REQUEST_ERROR', 'resource': resource,
                        'httpStatus': getattr(error, 'status', None), 'timeout': isinstance(error, TimeoutError)})
                if isinstance(error, ProtectedRequestFailure) and error.status not in (404, 429, 500, 502, 503, 504):
                    raise
        sleep(min(2, max(0, end-clock())))
    raise TimeoutError('Exact job absence not verified before cleanup deadline')



# Owner-approved probe diagnostics/service-cleanup successor; historical source is retained.
PROBE_DIAGNOSTICS_APPROVAL_SHA256 = '1aa01b5f96e847cd1ce446e9f4b7f47da46e44630a20ef2c8fa4abcef6e95bdc'
JAVA_SOURCE = 'import java.io.*;\nimport java.sql.*;\nimport org.flywaydb.core.Flyway;\npublic class CinenestConnectivityProbe {\n\n static String probeStage="CONFIGURATION",probeExpected="NONE",probeMethod="NONE";\n static final String probeId=java.util.UUID.randomUUID().toString();\n static void stage(String name,String method,String expected){if(!java.util.Set.of("CONFIGURATION","DATABASE_IDENTITY_TLS","DATABASE_HISTORY_PRIVILEGES","DATABASE_DENIAL_0","DATABASE_DENIAL_1","DATABASE_DENIAL_2","DATABASE_DENIAL_3","DATABASE_DENIAL_4","SERVICE_TARGET","METADATA_ID_TOKEN","HTTP_HEALTH","HTTP_HOME","HTTP_ACCOUNT_DENIAL","STATIC_ASSET_DISCOVERY","HTTP_STATIC_ASSET","HTTP_CSRF_DENIAL").contains(name)||!java.util.Set.of("NONE","GET","POST","SQL_READ","SQL_DENIAL").contains(method)||!java.util.Set.of("NONE","IDENTITY_TLS","44_RESTRICTED","42501","PINNED_TARGET","200","401_OR_403","ASSET_PRESENT","200_NONEMPTY","403").contains(expected))throw new IllegalArgumentException();probeStage=name;probeMethod=method;probeExpected=expected;System.err.println("CINENEST_PROBE stage="+probeStage+" id="+probeId+" method="+probeMethod+" expected="+probeExpected+" event=BEGIN");}\n static String failureClass(Throwable error){\n  if(error instanceof java.net.http.HttpTimeoutException)return "TIMEOUT";\n  if(error instanceof javax.net.ssl.SSLException)return "TLS";\n  if(error instanceof java.sql.SQLException sql){String code=sql.getSQLState();return "SQL_"+(code!=null&&code.matches("[0-9A-Z]{5}")?code:"UNKNOWN");}\n  if(error instanceof java.io.IOException)return "TRANSPORT";\n  if(error instanceof IllegalArgumentException)return "CONFIGURATION";\n  return "CONTRACT";\n }\n static void failure(Throwable error){System.err.println("CINENEST_PROBE stage="+probeStage+" id="+probeId+" method="+probeMethod+" expected="+probeExpected+" class="+failureClass(error)+" retry=NONE mutating="+(probeMethod.equals("POST")||probeMethod.equals("SQL_DENIAL"))+" outcome="+((probeMethod.equals("POST")||probeMethod.equals("SQL_DENIAL"))?"UNCERTAIN":"FAILED"));}\n static <T> java.net.http.HttpResponse<T> observed(java.net.http.HttpResponse<T> response){\n  String raw=response.headers().firstValue("Content-Type").orElse("").split(";",2)[0].toLowerCase(java.util.Locale.ROOT);\n  String type=java.util.Set.of("text/html","application/json","text/plain","application/javascript","text/css").contains(raw)?raw:"OTHER";\n  Object body=response.body();long length=body instanceof String text?text.length():body instanceof byte[] bytes?bytes.length:0;\n  System.err.println("CINENEST_PROBE stage="+probeStage+" id="+probeId+" expected="+probeExpected+" actual="+response.statusCode()+" type="+type+" length="+Math.min(length,1048577));if(!(probeExpected.equals("401_OR_403")?(response.statusCode()==401||response.statusCode()==403):probeExpected.equals("403")?response.statusCode()==403:response.statusCode()==200))throw new IllegalStateException();return response;\n }\n public static void main(String[] args) {\n  try { execute(args); } catch(Throwable failure) { failure(failure); System.exit(1); }\n }\n static void execute(String[] args) throws Exception {\n  if(args[0].equals("DEPENDENCIES")){System.out.println(Flyway.configure().getClass().getName());return;}\n  String url=System.getenv("CINENEST_DB_URL"),user=System.getenv("CINENEST_DB_USER"),password=System.getenv("CINENEST_DB_PASSWORD");\n  if(url==null||user==null||password==null||password.isBlank())throw new IllegalArgumentException();\n  if(!url.equals("jdbc:postgresql://ddc7a2d85eb1.18o4rx1o80d5g.us-east1.sql.goog.:5432/cinenest_connectivity_qualification?sslmode=verify-full&sslrootcert=/run/secrets/db-ca.pem"))throw new IllegalArgumentException("Unexpected isolated JDBC target");\n  if(!user.equals("cinenest_qualification_runtime")&&!user.equals("cinenest_qualification_migrator"))throw new IllegalArgumentException("Unexpected SQL role");\n\n  stage("DATABASE_IDENTITY_TLS","SQL_READ","IDENTITY_TLS");\n  try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()){\n   var r=s.executeQuery("SELECT current_user,ssl FROM pg_stat_ssl WHERE pid=pg_backend_pid()");if(!r.next()||!user.equals(r.getString(1))||!r.getBoolean(2))throw new IllegalStateException("Identity/TLS mismatch");\n  }\n  if(args[0].equals("PROBE")){\n   if(!user.equals("cinenest_qualification_runtime"))throw new IllegalArgumentException();\n   stage("DATABASE_HISTORY_PRIVILEGES","SQL_READ","44_RESTRICTED");\n   try(var c=DriverManager.getConnection(url,user,password);var st=c.createStatement()){\n    var rr=st.executeQuery("SELECT count(*) FROM public.flyway_schema_history WHERE success AND version IS NOT NULL");rr.next();if(rr.getInt(1)!=44)throw new IllegalStateException();\n    rr=st.executeQuery("SELECT NOT rolsuper AND NOT rolcreatedb AND NOT rolcreaterole AND NOT rolreplication AND NOT rolbypassrls AND NOT has_database_privilege(current_user,current_database(),\'CREATE\') AND NOT has_database_privilege(current_user,current_database(),\'TEMP\') AND NOT pg_has_role(current_user,\'cinenest_qualification_migrator\',\'MEMBER\') FROM pg_roles WHERE rolname=current_user");if(!rr.next()||!rr.getBoolean(1))throw new IllegalStateException();\n   }\n   try(var c=DriverManager.getConnection(url,user,password);var st=c.createStatement()){\n    for(String deniedSql:new String[]{"CREATE TABLE public.cinenest_qualification_permission_probe(id integer)","CREATE TEMP TABLE cinenest_qualification_permission_probe(id integer)","CREATE ROLE cinenest_qualification_must_be_denied NOLOGIN","SELECT * FROM cinenest_identity.account","SET ROLE cinenest_qualification_migrator"}){\n     stage("DATABASE_DENIAL_"+java.util.List.of("CREATE TABLE public.cinenest_qualification_permission_probe(id integer)","CREATE TEMP TABLE cinenest_qualification_permission_probe(id integer)","CREATE ROLE cinenest_qualification_must_be_denied NOLOGIN","SELECT * FROM cinenest_identity.account","SET ROLE cinenest_qualification_migrator").indexOf(deniedSql),"SQL_DENIAL","42501");\n     c.setAutoCommit(false);try{st.execute(deniedSql);throw new IllegalStateException("Unexpected SQL authorization");}catch(SQLException error){if(!"42501".equals(error.getSQLState()))throw error;}finally{c.rollback();c.setAutoCommit(true);}\n    }\n   }\n   stage("SERVICE_TARGET","NONE","PINNED_TARGET");\n   var target=java.net.URI.create(System.getenv("CINENEST_QUALIFICATION_SERVICE_URL"));\n   if(!target.getScheme().equals("https")||target.getUserInfo()!=null||target.getQuery()!=null||target.getFragment()!=null||!target.getHost().startsWith("cinenest-connectivity-qualification-")||!target.getHost().endsWith(".run.app")||(!target.getPath().isEmpty()&&!target.getPath().equals("/")))throw new IllegalArgumentException();\n   var client=java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).followRedirects(java.net.http.HttpClient.Redirect.NEVER).build();\n   var metadata=java.net.URI.create("http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/identity?audience="+java.net.URLEncoder.encode(target.toString(),java.nio.charset.StandardCharsets.UTF_8)+"&format=full");\n   stage("METADATA_ID_TOKEN","GET","200");\n   var tokenResult=observed(client.send(java.net.http.HttpRequest.newBuilder(metadata).timeout(java.time.Duration.ofSeconds(10)).header("Metadata-Flavor","Google").GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString()));if(tokenResult.statusCode()!=200)throw new IllegalStateException();String token=tokenResult.body();\n   String html=null;\n   for(String path:new String[]{"/healthz","/","/api/accounts/me"}){\n    stage(path.equals("/healthz")?"HTTP_HEALTH":path.equals("/")?"HTTP_HOME":"HTTP_ACCOUNT_DENIAL","GET",path.equals("/api/accounts/me")?"401_OR_403":"200");\n    var response=observed(client.send(java.net.http.HttpRequest.newBuilder(target.resolve(path)).timeout(java.time.Duration.ofSeconds(15)).header("X-CineNest-Probe-ID",probeId).header("Authorization","Bearer "+token).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString()));\n    if(path.equals("/api/accounts/me")){if(response.statusCode()!=401&&response.statusCode()!=403)throw new IllegalStateException();}\n    else if(response.statusCode()!=200)throw new IllegalStateException();\n    if(path.equals("/"))html=response.body();\n    if(path.equals("/healthz")&&!response.body().contains("UP"))throw new IllegalStateException();\n   }\n   stage("STATIC_ASSET_DISCOVERY","NONE","ASSET_PRESENT");\n   var asset=java.util.regex.Pattern.compile("/_next/static/[^\\\\\\"\'<>\\\\s]+").matcher(html);if(!asset.find())throw new IllegalStateException();\n   stage("HTTP_STATIC_ASSET","GET","200_NONEMPTY");\n   var staticResponse=observed(client.send(java.net.http.HttpRequest.newBuilder(target.resolve(asset.group())).timeout(java.time.Duration.ofSeconds(15)).header("X-CineNest-Probe-ID",probeId).header("Authorization","Bearer "+token).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofByteArray()));if(staticResponse.statusCode()!=200||staticResponse.body().length==0)throw new IllegalStateException();\n   stage("HTTP_CSRF_DENIAL","POST","403");\n   var denied=observed(client.send(java.net.http.HttpRequest.newBuilder(target.resolve("/api/session/logout")).timeout(java.time.Duration.ofSeconds(15)).header("X-CineNest-Probe-ID",probeId).header("Authorization","Bearer "+token).header("Origin","https://wrong.example").POST(java.net.http.HttpRequest.BodyPublishers.noBody()).build(),java.net.http.HttpResponse.BodyHandlers.discarding()));if(denied.statusCode()!=403)throw new IllegalStateException();\n   token=null;System.out.println("PRIVATE_JDBC_RUNTIME_AND_AUTHENTICATED_HTTP_PROBE:PASS");return;\n  }\n\n  if(args[0].equals("MIGRATE")){\n   if(!user.equals("cinenest_qualification_migrator"))throw new IllegalArgumentException();\n   var f=Flyway.configure().dataSource(url,user,password).locations("filesystem:"+args[1]).cleanDisabled(true).load();\n   if(f.migrate().migrationsExecuted!=44)throw new IllegalStateException("Expected44migrations");f.validate();\n   if(!f.info().current().getVersion().toString().equals("45")||f.info().pending().length!=0)throw new IllegalStateException("Wrong Flyway state");\n   try(var c=DriverManager.getConnection(url,user,password);var st=c.createStatement()){st.execute(java.nio.file.Files.readString(java.nio.file.Path.of("/app/sql/02-post-migration-runtime-grants.sql.proposed")));}\n   System.out.println("MIGRATE_VALIDATE_AND_GRANTS:44:V45:PASS");return;\n  }\n\n  throw new IllegalArgumentException("Unexpected mode");\n }\n}\n'

_PREDECESSOR_BINDING_CLEANUP = cleanup_binding_with_parent_check
SERVICE_PARENT_URL = 'https://run.googleapis.com/v1/projects/'+PROJECT+'/locations/us-east1/services/cinenest-connectivity-qualification'


def cleanup_service_binding(binding,get_parent,remove,receipt,record):
    """Resolve only the pinned original service after acknowledged deletion and fresh absence."""
    if binding.get('url')!=SERVICE_PARENT_URL:
        raise ValueError('Service cleanup resource outside scope')
    if (not isinstance(receipt,dict) or receipt.get('url')!=SERVICE_PARENT_URL
            or not isinstance(receipt.get('uid'),str) or not receipt['uid']
            or receipt.get('deletionAcknowledged') is not True):
        raise ValueError('Owned service identity/deletion acknowledgement required')
    parent=get_parent(SERVICE_PARENT_URL)
    if parent is None:
        record({'event':'SERVICE_BINDING_PARENT_ALREADY_ABSENT','resource':SERVICE_PARENT_URL,'uid':receipt['uid'],'verification':'GET_404'})
        return
    uid=parent.get('uid') or parent.get('metadata',{}).get('uid')
    if uid!=receipt['uid']:raise ValueError('Service parent replaced; cleanup refused')
    try:return remove(binding)
    except ProtectedRequestFailure as error:
        record({'event':'SERVICE_BINDING_REMOVAL_ERROR','resource':SERVICE_PARENT_URL,'httpStatus':error.status})
        if error.status!=404 or get_parent(SERVICE_PARENT_URL) is not None:raise
        record({'event':'SERVICE_BINDING_PARENT_REMOVAL_VERIFIED','resource':SERVICE_PARENT_URL,'originalHttpStatus':404})



def delete_owned_service(receipt,api,record,deadline,save=lambda:None,clock=time.monotonic,sleep=time.sleep):
    if (not isinstance(receipt,dict) or receipt.get('url')!=SERVICE_PARENT_URL
            or not isinstance(receipt.get('uid'),str) or not receipt['uid']):
        raise ValueError('Exact owned service receipt required')
    if type(deadline) not in (int,float) or not math.isfinite(deadline) or clock()>=deadline:
        raise TimeoutError('Service cleanup deadline exhausted before mutation')
    target='https://run.googleapis.com/v2/projects/'+PROJECT+'/locations/us-east1/services/cinenest-connectivity-qualification'
    current=api('GET',target,missing=True)
    if current is not None:
        if current.get('uid')!=receipt['uid'] or not current.get('etag'):
            raise ValueError('Service identity/etag drift; deletion refused')
        try:
            from urllib.parse import quote
            result=api('DELETE',target+'?etag='+quote(current['etag'],safe=''))
            if result.get('error'):raise RuntimeError('Service deletion operation rejected')
            receipt['deletionAcknowledged']=True;save()
            record({'event':'SERVICE_DELETE_ACCEPTED','uid':receipt['uid'],'operation':result.get('name')})
        except (ProtectedRequestFailure,TimeoutError) as error:
            record({'event':'SERVICE_DELETE_REQUEST_ERROR','httpStatus':getattr(error,'status',None),'uncertain':True})
            if isinstance(error,ProtectedRequestFailure) and error.status not in (404,429,500,502,503,504):raise
    def observe():
        item=api('GET',target,missing=True)
        return None if item is None else {'id':item.get('uid')}
    result=owned_deletion_convergence(observe,receipt['uid'],lambda:None,record,deadline,clock=clock,sleep=sleep)
    receipt['deletionAcknowledged']=True;save()
    return result

_PREDECESSOR_MONITORING_ARTIFACTS=monitoring_cleanup_artifacts

def monitoring_cleanup_artifacts(controller,helpers,private_helper):
    result=_PREDECESSOR_MONITORING_ARTIFACTS(controller,helpers,private_helper)
    text=result['cloud-session.py']
    old="p.write_text(template);gc('create-internal-service',['run','services','replace',str(p),'--region=us-east1','--async'],60);state['createdService']=True;save();resource="
    new="p.write_text(template);_,raw,_=gc('create-internal-service',['run','services','replace',str(p),'--region=us-east1','--async'],60);state['createdService']=True;state['serviceReceipt']={'url':M['SERVICE_PARENT_URL'],'uid':json.loads(raw)['metadata']['uid'],'deletionAcknowledged':False};assert state['serviceReceipt']['uid'];save();resource="
    if text.count(old)!=1:raise ValueError('Service creation boundary drift')
    text=text.replace(old,new,1)
    old=" if state['createdService']:clean('internal-service',lambda:gc('delete-service',['run','services','delete',service,'--region=us-east1'],90))"
    new=""" if state['createdService']:
  clean('internal-service',lambda:M['delete_owned_service'](state.get('serviceReceipt'),api,monitor_record,deadline-480,save))"""
    if text.count(old)!=1:raise ValueError('Service deletion boundary drift')
    text=text.replace(old,new,1)
    old="M['cleanup_binding_with_parent_check'](b,lambda url:api('GET',url,missing=True),remove_binding,state.get('jobUids',{}),monitor_record)"
    new="M['cleanup_service_binding'](b,lambda url:api('GET',url,missing=True),remove_binding,state.get('serviceReceipt'),monitor_record) if b['url']==M['SERVICE_PARENT_URL'] else "+old
    if text.count(old)!=1:raise ValueError('Service binding boundary drift')
    text=text.replace(old,new,1).replace("v['execution_monitoring_errors']()","v['probe_diagnostics_errors']()")
    compile(text,'cloud-session.py','exec');result['cloud-session.py']=text
    return result



# Exact Owner-approved retained-history successor;previous source and SQL stay immutable.
RETAINED_FLYWAY_APPROVAL_SHA256 = 'ebe105395fe33bc314d9e82b9eb7b669c0ae3dc3d30d3cb6c096810823d5394f'
RETAINED_SCHEMA_SHA256 = "ee88078bdee65baf255f6cb77040c9a89ae4d924b2b279d4c01bb5e335b5af1d"
_RETAINED_JAVA_TEMPLATE = 'import java.io.*;\nimport java.sql.*;\nimport org.flywaydb.core.Flyway;\npublic class CinenestConnectivityProbe {\n\n static String probeStage="CONFIGURATION",probeExpected="NONE",probeMethod="NONE";\n static final String probeId=java.util.UUID.randomUUID().toString();\n static void stage(String name,String method,String expected){if(!java.util.Set.of("CONFIGURATION","DATABASE_IDENTITY_TLS","DATABASE_HISTORY_PRIVILEGES","DATABASE_DENIAL_0","DATABASE_DENIAL_1","DATABASE_DENIAL_2","DATABASE_DENIAL_3","DATABASE_DENIAL_4","SERVICE_TARGET","METADATA_ID_TOKEN","HTTP_HEALTH","HTTP_HOME","HTTP_ACCOUNT_DENIAL","STATIC_ASSET_DISCOVERY","HTTP_STATIC_ASSET","HTTP_CSRF_DENIAL","MIGRATION_CLASSIFY","MIGRATION_FRESH_EXECUTE","MIGRATION_VALIDATE","MIGRATION_SCHEMA").contains(name)||!java.util.Set.of("NONE","GET","POST","SQL_READ","SQL_DENIAL","SQL_MIGRATE").contains(method)||!java.util.Set.of("NONE","IDENTITY_TLS","44_RESTRICTED","42501","PINNED_TARGET","200","401_OR_403","ASSET_PRESENT","200_NONEMPTY","403","44_V45","SCHEMA_MATCH","FRESH_AUTHORIZED").contains(expected))throw new IllegalArgumentException();probeStage=name;probeMethod=method;probeExpected=expected;System.err.println("CINENEST_PROBE stage="+probeStage+" id="+probeId+" method="+probeMethod+" expected="+probeExpected+" event=BEGIN");}\n static String failureClass(Throwable error){\n  if(error instanceof java.net.http.HttpTimeoutException)return "TIMEOUT";\n  if(error instanceof javax.net.ssl.SSLException)return "TLS";\n  if(error instanceof java.sql.SQLException sql){String code=sql.getSQLState();return "SQL_"+(code!=null&&code.matches("[0-9A-Z]{5}")?code:"UNKNOWN");}\n  if(error instanceof java.io.IOException)return "TRANSPORT";\n  if(error instanceof IllegalArgumentException)return "CONFIGURATION";\n  return "CONTRACT";\n }\n static void failure(Throwable error){System.err.println("CINENEST_PROBE stage="+probeStage+" id="+probeId+" method="+probeMethod+" expected="+probeExpected+" class="+failureClass(error)+" retry=NONE mutating="+(probeMethod.equals("POST")||probeMethod.equals("SQL_DENIAL")||probeMethod.equals("SQL_MIGRATE"))+" outcome="+((probeMethod.equals("POST")||probeMethod.equals("SQL_DENIAL")||probeMethod.equals("SQL_MIGRATE"))?"UNCERTAIN":"FAILED"));}\n static <T> java.net.http.HttpResponse<T> observed(java.net.http.HttpResponse<T> response){\n  String raw=response.headers().firstValue("Content-Type").orElse("").split(";",2)[0].toLowerCase(java.util.Locale.ROOT);\n  String type=java.util.Set.of("text/html","application/json","text/plain","application/javascript","text/css").contains(raw)?raw:"OTHER";\n  Object body=response.body();long length=body instanceof String text?text.length():body instanceof byte[] bytes?bytes.length:0;\n  System.err.println("CINENEST_PROBE stage="+probeStage+" id="+probeId+" expected="+probeExpected+" actual="+response.statusCode()+" type="+type+" length="+Math.min(length,1048577));if(!(probeExpected.equals("401_OR_403")?(response.statusCode()==401||response.statusCode()==403):probeExpected.equals("403")?response.statusCode()==403:response.statusCode()==200))throw new IllegalStateException();return response;\n }\n public static void main(String[] args) {\n  try { execute(args); } catch(Throwable failure) { failure(failure); System.exit(1); }\n }\n static void execute(String[] args) throws Exception {\n  if(args[0].equals("DEPENDENCIES")){System.out.println(Flyway.configure().getClass().getName());return;}\n  String url=System.getenv("CINENEST_DB_URL"),user=System.getenv("CINENEST_DB_USER"),password=System.getenv("CINENEST_DB_PASSWORD");\n  if(url==null||user==null||password==null||password.isBlank())throw new IllegalArgumentException();\n  if(!url.equals("jdbc:postgresql://ddc7a2d85eb1.18o4rx1o80d5g.us-east1.sql.goog.:5432/cinenest_connectivity_qualification?sslmode=verify-full&sslrootcert=/run/secrets/db-ca.pem"))throw new IllegalArgumentException("Unexpected isolated JDBC target");\n  if(!user.equals("cinenest_qualification_runtime")&&!user.equals("cinenest_qualification_migrator"))throw new IllegalArgumentException("Unexpected SQL role");\n\n  stage("DATABASE_IDENTITY_TLS","SQL_READ","IDENTITY_TLS");\n  try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()){\n   var r=s.executeQuery("SELECT current_user,ssl FROM pg_stat_ssl WHERE pid=pg_backend_pid()");if(!r.next()||!user.equals(r.getString(1))||!r.getBoolean(2))throw new IllegalStateException("Identity/TLS mismatch");\n  }\n  if(args[0].equals("PROBE")){\n   if(!user.equals("cinenest_qualification_runtime"))throw new IllegalArgumentException();\n   stage("DATABASE_HISTORY_PRIVILEGES","SQL_READ","44_RESTRICTED");\n   try(var c=DriverManager.getConnection(url,user,password);var st=c.createStatement()){\n    var rr=st.executeQuery("SELECT count(*) FROM public.flyway_schema_history WHERE success AND version IS NOT NULL");rr.next();if(rr.getInt(1)!=44)throw new IllegalStateException();\n    rr=st.executeQuery("SELECT NOT rolsuper AND NOT rolcreatedb AND NOT rolcreaterole AND NOT rolreplication AND NOT rolbypassrls AND NOT has_database_privilege(current_user,current_database(),\'CREATE\') AND NOT has_database_privilege(current_user,current_database(),\'TEMP\') AND NOT pg_has_role(current_user,\'cinenest_qualification_migrator\',\'MEMBER\') FROM pg_roles WHERE rolname=current_user");if(!rr.next()||!rr.getBoolean(1))throw new IllegalStateException();\n   }\n   try(var c=DriverManager.getConnection(url,user,password);var st=c.createStatement()){\n    for(String deniedSql:new String[]{"CREATE TABLE public.cinenest_qualification_permission_probe(id integer)","CREATE TEMP TABLE cinenest_qualification_permission_probe(id integer)","CREATE ROLE cinenest_qualification_must_be_denied NOLOGIN","SELECT * FROM cinenest_identity.account","SET ROLE cinenest_qualification_migrator"}){\n     stage("DATABASE_DENIAL_"+java.util.List.of("CREATE TABLE public.cinenest_qualification_permission_probe(id integer)","CREATE TEMP TABLE cinenest_qualification_permission_probe(id integer)","CREATE ROLE cinenest_qualification_must_be_denied NOLOGIN","SELECT * FROM cinenest_identity.account","SET ROLE cinenest_qualification_migrator").indexOf(deniedSql),"SQL_DENIAL","42501");\n     c.setAutoCommit(false);try{st.execute(deniedSql);throw new IllegalStateException("Unexpected SQL authorization");}catch(SQLException error){if(!"42501".equals(error.getSQLState()))throw error;}finally{c.rollback();c.setAutoCommit(true);}\n    }\n   }\n   stage("SERVICE_TARGET","NONE","PINNED_TARGET");\n   var target=java.net.URI.create(System.getenv("CINENEST_QUALIFICATION_SERVICE_URL"));\n   if(!target.getScheme().equals("https")||target.getUserInfo()!=null||target.getQuery()!=null||target.getFragment()!=null||!target.getHost().startsWith("cinenest-connectivity-qualification-")||!target.getHost().endsWith(".run.app")||(!target.getPath().isEmpty()&&!target.getPath().equals("/")))throw new IllegalArgumentException();\n   var client=java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).followRedirects(java.net.http.HttpClient.Redirect.NEVER).build();\n   var metadata=java.net.URI.create("http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/identity?audience="+java.net.URLEncoder.encode(target.toString(),java.nio.charset.StandardCharsets.UTF_8)+"&format=full");\n   stage("METADATA_ID_TOKEN","GET","200");\n   var tokenResult=observed(client.send(java.net.http.HttpRequest.newBuilder(metadata).timeout(java.time.Duration.ofSeconds(10)).header("Metadata-Flavor","Google").GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString()));if(tokenResult.statusCode()!=200)throw new IllegalStateException();String token=tokenResult.body();\n   String html=null;\n   for(String path:new String[]{"/healthz","/","/api/accounts/me"}){\n    stage(path.equals("/healthz")?"HTTP_HEALTH":path.equals("/")?"HTTP_HOME":"HTTP_ACCOUNT_DENIAL","GET",path.equals("/api/accounts/me")?"401_OR_403":"200");\n    var response=observed(client.send(java.net.http.HttpRequest.newBuilder(target.resolve(path)).timeout(java.time.Duration.ofSeconds(15)).header("X-CineNest-Probe-ID",probeId).header("Authorization","Bearer "+token).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString()));\n    if(path.equals("/api/accounts/me")){if(response.statusCode()!=401&&response.statusCode()!=403)throw new IllegalStateException();}\n    else if(response.statusCode()!=200)throw new IllegalStateException();\n    if(path.equals("/"))html=response.body();\n    if(path.equals("/healthz")&&!response.body().contains("UP"))throw new IllegalStateException();\n   }\n   stage("STATIC_ASSET_DISCOVERY","NONE","ASSET_PRESENT");\n   var asset=java.util.regex.Pattern.compile("/_next/static/[^\\\\\\"\'<>\\\\s]+").matcher(html);if(!asset.find())throw new IllegalStateException();\n   stage("HTTP_STATIC_ASSET","GET","200_NONEMPTY");\n   var staticResponse=observed(client.send(java.net.http.HttpRequest.newBuilder(target.resolve(asset.group())).timeout(java.time.Duration.ofSeconds(15)).header("X-CineNest-Probe-ID",probeId).header("Authorization","Bearer "+token).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofByteArray()));if(staticResponse.statusCode()!=200||staticResponse.body().length==0)throw new IllegalStateException();\n   stage("HTTP_CSRF_DENIAL","POST","403");\n   var denied=observed(client.send(java.net.http.HttpRequest.newBuilder(target.resolve("/api/session/logout")).timeout(java.time.Duration.ofSeconds(15)).header("X-CineNest-Probe-ID",probeId).header("Authorization","Bearer "+token).header("Origin","https://wrong.example").POST(java.net.http.HttpRequest.BodyPublishers.noBody()).build(),java.net.http.HttpResponse.BodyHandlers.discarding()));if(denied.statusCode()!=403)throw new IllegalStateException();\n   token=null;System.out.println("PRIVATE_JDBC_RUNTIME_AND_AUTHENTICATED_HTTP_PROBE:PASS");return;\n  }\n\n  if(args[0].equals("MIGRATE")){\n   if(!user.equals("cinenest_qualification_migrator"))throw new IllegalArgumentException();\n   var f=Flyway.configure().dataSource(url,user,password).locations("filesystem:"+args[1]).cleanDisabled(true).baselineOnMigrate(false).outOfOrder(false).ignoreMigrationPatterns(new String[0]).target("45").load();\n   String expected=System.getenv("CINENEST_MIGRATION_EXPECTED_STATE");\n   if(expected==null)reject(MigrationFailure.UNEXPECTED_STATE);\n   var report=qualifyMigration(f,java.nio.file.Path.of(args[1]),expected);\n   System.out.println("MIGRATION_QUALIFICATION:"+report.state()+":NEW="+report.newMigrations()+":HISTORY=44:V45:PASS");return;\n  }\n\n  throw new IllegalArgumentException("Unexpected mode");\n }\n static final java.util.Map<String,String> EXPECTED_MIGRATION_FILES=java.util.Map.ofEntries(java.util.Map.entry("V10__synthetic_credit_allocation.sql","766b5e1cfaf93727937a13549705ef6c14d18dcd86c33e8b0ca7188b64f7f493"),\njava.util.Map.entry("V11__scoped_admin_read_audit.sql","21896b150a5a15c65dd5f17f865192d44a726e1f195163a08daad2e792558e29"),\njava.util.Map.entry("V12__synthetic_bank_refunds.sql","e9383d0f67240bcb50bea0cfed3b20e6897489e4b9ec23e80fb22a70f1b8465f"),\njava.util.Map.entry("V13__synthetic_access.sql","479c711653a1dce5010c930a11db432c4acdbcc0208f7f8a5d0dc0bd02633d8b"),\njava.util.Map.entry("V14__synthetic_notifications.sql","8b821fe4df26b21d996d3da097fc77fd36b27afaf0abbc3a0de0f2248d20118f"),\njava.util.Map.entry("V15__synthetic_booking_amendments.sql","74c68f2b0b1e4fb91f8a266db97f74e9578375d04429fd6bea605ca237344b55"),\njava.util.Map.entry("V16__synthetic_lighting.sql","a632d18ac7602951e40016f544886055072f11457385933228c27726f55a43df"),\njava.util.Map.entry("V17__synthetic_room_turnover.sql","d353b1e7258aed6cb1bb647cb3124dfdfd874e030a43fdcd028b25a995894ea3"),\njava.util.Map.entry("V18__synthetic_guest_roster.sql","9f5a2b064f382a8e22f9bd58d8c4ff69972ed7f21a7edd2d882507abb74af29c"),\njava.util.Map.entry("V19__staff_booking_read_audit.sql","67064d3b6939dc717b8c2d9d4d99f3b740004e6c4ee731841108c7a8acd02df6"),\njava.util.Map.entry("V1__foundation_namespace.sql","fd31a33e9a5fbcdfa61ef619559a1669d6674b76036b129631b19ac3293b92f1"),\njava.util.Map.entry("V20__managed_grant_read_audit.sql","82ec3c777478332258339f85898473b1f6a1e1c0749eef4d1ba97ed69e395ed6"),\njava.util.Map.entry("V21__maintenance_impact_read_audit.sql","3bb99640aaee87ecceea74d13a3bad543fc2c6fa17ee0a28969b54531dfae3c7"),\njava.util.Map.entry("V22__booking_confirmation_report_read_audit.sql","ed5a1c482744d76d8a2acb4054859f2902d71f849e49eff4eb53929c2d1b563e"),\njava.util.Map.entry("V23__popular_session_starts_read_audit.sql","13ddd9296f2e31c5db62425303a6b48609a8d0624ecc86efba0026a54f1693bb"),\njava.util.Map.entry("V24__synthetic_booking_cancellation.sql","aa79391ae3cfa07f7b32f248055d321471eddb5ccdb839b6a108097da11344c4"),\njava.util.Map.entry("V25__synthetic_extension_reservations.sql","b90cbb93256b8b89d9dcf88e9925a251b4f5842aeca229e7417297359d912223"),\njava.util.Map.entry("V26__staging_bank_instruction_reads.sql","e23d528454e17f1bb9d9cff2dd2536a1b6543c6d73b37056b4ff3eba57faed42"),\njava.util.Map.entry("V27__inactive_smart_lock_registry.sql","c9164abae01706f23588aeae077bdfd5810b5c14af54ba471156d27785566533"),\njava.util.Map.entry("V28__local_manual_customer_identity.sql","f7f18489b2d73fdbe164bc43581e286f77f70b5c67407140267080e0d579b4a8"),\njava.util.Map.entry("V29__local_customer_profile.sql","742ad5bd9714ddf7fdc5882fb268b64c8f5045fcd94afc27b0017cdefa1bf8af"),\njava.util.Map.entry("V2__identity_accounts_grants_sessions.sql","6794628d83c27062205d473a84808a293592ec8c31f9e9b50f26038e13f1e2c1"),\njava.util.Map.entry("V30__local_venue_banners.sql","dd0dd41e961db65eb5c64ed029b5eb4553586789eff68db4934d9b05e4bdb6ce"),\njava.util.Map.entry("V32__local_room_blocking.sql","2ed7d85229324cf5f1cb1fae9028977378289a8454bddbb9e807b01c4087f2aa"),\njava.util.Map.entry("V33__qualification_room_lock_assignments.sql","401aeeefc90e6721a5e4b48adb621ebea9c779b286c01315eb2b9c1a81cf390c"),\njava.util.Map.entry("V34__staging_bank_instruction_publication.sql","50a49d850ff0e10668d622cee1eaab5a1fdf58ebdebe71f287aba706f3e2df98"),\njava.util.Map.entry("V35__local_synthetic_credential_recovery.sql","2aca51352c8d3a8352e1df8bdbeea9661210a4b21a51ced5bb7046e25bd32aaa"),\njava.util.Map.entry("V36__commercial_policy_drafts.sql","bee769c1bed44bd52d4887975760f8775308945794c94c00b30e546ec39e7505"),\njava.util.Map.entry("V37__commercial_policy_binding.sql","5b8300720fa412956c0e607fadb6e800ffccc3618e5cdaa989a771c760a6ecb5"),\njava.util.Map.entry("V38__commercial_policy_trusted_approval_intake.sql","8673fae44f1b365225777650e7a2815bcbeac7ad5a5404eeeb13d8995f7509fa"),\njava.util.Map.entry("V39__commercial_policy_activation.sql","9c8ded0d5b787b6e9db8127bb3c7887826e7f6c0bb957e3f96bcf1fdc79074ce"),\njava.util.Map.entry("V3__venue_room_catalog.sql","f2b25430b30177ffc90f1daf0dbaff415ca5bc8102536aee5a46a4c32ec4fc54"),\njava.util.Map.entry("V40__commercial_quotation_issuance.sql","e9c6b2c02d7134d5eeab64d409019f906be6ade58cf5733f18e12228c4a3621e"),\njava.util.Map.entry("V41__customer_commercial_quotation.sql","bcd02dccaf20e08e0e8cb70106b65eaa6f6fa64ee1a1533d6d0d563426f8dd6b"),\njava.util.Map.entry("V42__operational_commercial_policy_and_quotation.sql","ecc551549d4c6dfaafe99e9d0c676f6163121a82ebd9380bab1009e1f3236ab2"),\njava.util.Map.entry("V43__synthetic_extension_funding_requests.sql","86974955cb6607d25cbaee0841ebd8558fcac07aa9d68eda85b42ee6a5104488"),\njava.util.Map.entry("V44__synthetic_staff_check_in.sql","b4013c7de3126abe382dd110b34348026e96ee92144834978fcd1edec70c17ca"),\njava.util.Map.entry("V45__synthetic_staff_session_events.sql","091e8c2cb241d919e4def29d8854e7372ffc7267fe392f0c4959251c70efd1df"),\njava.util.Map.entry("V4__package_drafts_and_minimum_policy.sql","a4897ec35d1e351df4d5902a71d247226cdfbb42c87c95e682e451240a30a298"),\njava.util.Map.entry("V5__indicative_package_publication.sql","825350b732a8f9e1bab0a958a42bcc08ed35a12f163cffe0297f52819112dac8"),\njava.util.Map.entry("V6__inventory_holds.sql","a93ae6f188d672c674b061319dc0e89063f8c4872a0a46afc29359483c72386f"),\njava.util.Map.entry("V7__synthetic_quotations.sql","22e67f40702d78b4914a64d854767d38e86d447251e0bb1e2cb713125f0f63ff"),\njava.util.Map.entry("V8__synthetic_verification.sql","5454a1af6c2b03687d1f6ee7ac038d55685e10710812e71910f87ab1dc8db2e3"),\njava.util.Map.entry("V9__synthetic_bank_requests.sql","da0ed3f446a2b44782d3550a37c25db0fe7e94f9804d5d6107bb728109da5b48"));\n static final String EXPECTED_SCHEMA_QUERY="WITH ns AS (\\n SELECT oid,nspname,nspowner,nspacl FROM pg_namespace\\n WHERE nspname !~ \'^pg_\' AND nspname<>\'information_schema\'\\n), rels AS (\\n SELECT c.*,n.nspname FROM pg_class c JOIN ns n ON n.oid=c.relnamespace\\n WHERE c.relkind IN (\'r\',\'p\',\'v\',\'m\',\'S\',\'f\',\'c\')\\n AND NOT EXISTS(SELECT 1 FROM pg_depend d WHERE d.classid=\'pg_class\'::regclass AND d.objid=c.oid AND d.deptype=\'e\')\\n), funcs AS (\\n SELECT p.*,n.nspname FROM pg_proc p JOIN ns n ON n.oid=p.pronamespace\\n WHERE NOT EXISTS(SELECT 1 FROM pg_depend d WHERE d.classid=\'pg_proc\'::regclass AND d.objid=p.oid AND d.deptype=\'e\')\\n), objects AS (\\n SELECT \'schema\' kind,nspname identity,jsonb_build_object(\'owner\',pg_get_userbyid(nspowner))::text details FROM ns\\n UNION ALL SELECT \'relation\',nspname||\'.\'||relname,jsonb_build_object(\'kind\',relkind,\'owner\',pg_get_userbyid(relowner),\'rowsecurity\',relrowsecurity,\'force\',relforcerowsecurity,\'persistence\',relpersistence,\'replident\',relreplident,\'options\',reloptions,\'partition\',CASE WHEN relkind=\'p\' THEN pg_get_partkeydef(oid) ELSE NULL END,\'bound\',pg_get_expr(relpartbound,oid),\'view\',CASE WHEN relkind IN (\'v\',\'m\') THEN pg_get_viewdef(oid,false) ELSE NULL END)::text FROM rels\\n UNION ALL SELECT \'column\',r.nspname||\'.\'||r.relname||\'.\'||a.attnum,jsonb_build_object(\'name\',a.attname,\'type\',format_type(a.atttypid,a.atttypmod),\'notnull\',a.attnotnull,\'identity\',a.attidentity,\'generated\',a.attgenerated,\'dropped\',a.attisdropped,\'default\',pg_get_expr(d.adbin,d.adrelid),\'collation\',CASE WHEN a.attcollation<>0 THEN a.attcollation::regcollation::text END)::text FROM rels r JOIN pg_attribute a ON a.attrelid=r.oid AND a.attnum>0 LEFT JOIN pg_attrdef d ON d.adrelid=r.oid AND d.adnum=a.attnum\\n UNION ALL SELECT \'constraint\',r.nspname||\'.\'||r.relname||\'.\'||c.conname,jsonb_build_object(\'definition\',pg_get_constraintdef(c.oid,false),\'validated\',c.convalidated,\'deferrable\',c.condeferrable,\'deferred\',c.condeferred)::text FROM rels r JOIN pg_constraint c ON c.conrelid=r.oid\\n UNION ALL SELECT \'index\',r.nspname||\'.\'||ic.relname,jsonb_build_object(\'definition\',pg_get_indexdef(i.indexrelid,0,false),\'valid\',i.indisvalid,\'ready\',i.indisready,\'unique\',i.indisunique,\'primary\',i.indisprimary,\'options\',ic.reloptions)::text FROM rels r JOIN pg_index i ON i.indrelid=r.oid JOIN pg_class ic ON ic.oid=i.indexrelid\\n UNION ALL SELECT \'trigger\',r.nspname||\'.\'||r.relname||\'.\'||t.tgname,jsonb_build_object(\'definition\',pg_get_triggerdef(t.oid,false),\'enabled\',t.tgenabled)::text FROM rels r JOIN pg_trigger t ON t.tgrelid=r.oid AND NOT t.tgisinternal\\n UNION ALL SELECT \'function\',nspname||\'.\'||proname||\'(\'||pg_get_function_identity_arguments(oid)||\')\',jsonb_build_object(\'definition\',pg_get_functiondef(oid),\'owner\',pg_get_userbyid(proowner))::text FROM funcs\\n UNION ALL SELECT \'sequence\',r.nspname||\'.\'||r.relname,jsonb_build_object(\'type\',format_type(s.seqtypid,NULL),\'start\',s.seqstart,\'increment\',s.seqincrement,\'max\',s.seqmax,\'min\',s.seqmin,\'cache\',s.seqcache,\'cycle\',s.seqcycle)::text FROM rels r JOIN pg_sequence s ON s.seqrelid=r.oid\\n UNION ALL SELECT \'type\',n.nspname||\'.\'||t.typname,jsonb_build_object(\'kind\',t.typtype,\'owner\',pg_get_userbyid(t.typowner),\'base\',format_type(t.typbasetype,t.typtypmod),\'notnull\',t.typnotnull,\'default\',t.typdefault,\'labels\',(SELECT jsonb_agg(e.enumlabel ORDER BY e.enumsortorder) FROM pg_enum e WHERE e.enumtypid=t.oid))::text FROM ns n JOIN pg_type t ON t.typnamespace=n.oid AND t.typtype IN (\'e\',\'d\')\\n UNION ALL SELECT \'domain_constraint\',n.nspname||\'.\'||t.typname||\'.\'||c.conname,pg_get_constraintdef(c.oid,false) FROM ns n JOIN pg_type t ON t.typnamespace=n.oid JOIN pg_constraint c ON c.contypid=t.oid\\n UNION ALL SELECT \'policy\',r.nspname||\'.\'||r.relname||\'.\'||p.polname,jsonb_build_object(\'cmd\',p.polcmd,\'permissive\',p.polpermissive,\'roles\',(SELECT jsonb_agg(CASE WHEN roleid=0 THEN \'PUBLIC\' ELSE pg_get_userbyid(roleid) END ORDER BY roleid::regrole::text) FROM unnest(p.polroles) roleid),\'qual\',pg_get_expr(p.polqual,p.polrelid),\'check\',pg_get_expr(p.polwithcheck,p.polrelid))::text FROM rels r JOIN pg_policy p ON p.polrelid=r.oid\\n UNION ALL SELECT \'extension\',e.extname,jsonb_build_object(\'version\',e.extversion,\'schema\',n.nspname,\'owner\',CASE WHEN e.extname=\'plpgsql\' THEN \'PROVIDER_BUILTIN\' ELSE pg_get_userbyid(e.extowner) END)::text FROM pg_extension e JOIN pg_namespace n ON n.oid=e.extnamespace\\n UNION ALL SELECT \'schema_acl\',n.nspname,jsonb_build_object(\'grantee\',CASE WHEN a.grantee=0 THEN \'PUBLIC\' ELSE pg_get_userbyid(a.grantee) END,\'grantor\',pg_get_userbyid(a.grantor),\'privilege\',a.privilege_type,\'grantable\',a.is_grantable)::text FROM ns n CROSS JOIN LATERAL aclexplode(coalesce(n.nspacl,acldefault(\'n\',n.nspowner))) a WHERE NOT(n.nspname=\'public\' AND a.grantee IN (SELECT oid FROM pg_roles WHERE rolname=\'cloudsqlsuperuser\'))\\n UNION ALL SELECT \'relation_acl\',r.nspname||\'.\'||r.relname,jsonb_build_object(\'grantee\',CASE WHEN a.grantee=0 THEN \'PUBLIC\' ELSE pg_get_userbyid(a.grantee) END,\'grantor\',pg_get_userbyid(a.grantor),\'privilege\',a.privilege_type,\'grantable\',a.is_grantable)::text FROM rels r CROSS JOIN LATERAL aclexplode(coalesce(r.relacl,acldefault(CASE WHEN r.relkind=\'S\' THEN \'s\'::\\"char\\" ELSE \'r\'::\\"char\\" END,r.relowner))) a\\n UNION ALL SELECT \'function_acl\',p.nspname||\'.\'||p.proname||\'(\'||pg_get_function_identity_arguments(p.oid)||\')\',jsonb_build_object(\'grantee\',CASE WHEN a.grantee=0 THEN \'PUBLIC\' ELSE pg_get_userbyid(a.grantee) END,\'grantor\',pg_get_userbyid(a.grantor),\'privilege\',a.privilege_type,\'grantable\',a.is_grantable)::text FROM funcs p CROSS JOIN LATERAL aclexplode(coalesce(p.proacl,acldefault(\'f\',p.proowner))) a\\n UNION ALL SELECT \'default_acl\',coalesce(n.nspname,\'GLOBAL\')||\'.\'||pg_get_userbyid(d.defaclrole)||\'.\'||d.defaclobjtype::text,jsonb_build_object(\'grantee\',CASE WHEN a.grantee=0 THEN \'PUBLIC\' ELSE pg_get_userbyid(a.grantee) END,\'grantor\',pg_get_userbyid(a.grantor),\'privilege\',a.privilege_type,\'grantable\',a.is_grantable)::text FROM pg_default_acl d LEFT JOIN ns n ON n.oid=d.defaclnamespace CROSS JOIN LATERAL aclexplode(d.defaclacl) a WHERE d.defaclnamespace=0 OR n.oid IS NOT NULL\\n)\\nSELECT kind,identity,details FROM objects ORDER BY kind COLLATE \\"C\\",identity COLLATE \\"C\\",details COLLATE \\"C\\"\\n";\n static final String EXPECTED_SCHEMA_SHA256="__SCHEMA_SHA__";\n enum MigrationFailure { ARTIFACTS,TARGET_IDENTITY,TLS,AUTHORIZATION,NONEMPTY_FRESH,UNEXPECTED_STATE,HISTORY_COUNT,HISTORY_RECORD,HISTORY_CHECKSUM,FLYWAY_VALIDATION,SCHEMA_DRIFT,SCHEMA_EXPECTATION,FRESH_COUNT,BUSY }\n static class MigrationContractException extends IllegalStateException {\n  final MigrationFailure reason;MigrationContractException(MigrationFailure reason){this.reason=reason;}\n }\n static void reject(MigrationFailure reason){System.err.println("CINENEST_MIGRATION_REJECT reason="+reason.name()+" stage="+probeStage+" id="+probeId);throw new MigrationContractException(reason);}\n record MigrationReport(String state,int newMigrations,int historyRows,String schemaSha256){}\n static String hex(byte[] bytes){return java.util.HexFormat.of().formatHex(bytes);}\n static void verifyMigrationArtifacts(java.nio.file.Path root)throws Exception{\n  var actual=new java.util.TreeMap<String,String>();\n  try(var files=java.nio.file.Files.list(root)){\n   for(var path:files.toList()){\n    if(java.nio.file.Files.isSymbolicLink(path)||!java.nio.file.Files.isRegularFile(path))reject(MigrationFailure.ARTIFACTS);\n    actual.put(path.getFileName().toString(),hex(java.security.MessageDigest.getInstance("SHA-256").digest(java.nio.file.Files.readAllBytes(path))));\n   }\n  }\n  if(!actual.equals(EXPECTED_MIGRATION_FILES))reject(MigrationFailure.ARTIFACTS);\n }\n static void verifyMigrationIdentity(java.sql.Connection c)throws Exception{\n  try(var s=c.createStatement()){\n   var r=s.executeQuery("SELECT current_database(),current_user,session_user,pg_get_userbyid(datdba),current_setting(\'server_version_num\')::integer FROM pg_database WHERE datname=current_database()");\n   if(!r.next()||!r.getString(1).equals("cinenest_connectivity_qualification")||!r.getString(2).equals("cinenest_qualification_migrator")||!r.getString(3).equals("cinenest_qualification_migrator")||!r.getString(4).equals("cinenest_qualification_migrator")||r.getInt(5)!=180006)reject(MigrationFailure.TARGET_IDENTITY);\n   r=s.executeQuery("SELECT ssl FROM pg_stat_ssl WHERE pid=pg_backend_pid()");if(!r.next()||!r.getBoolean(1))reject(MigrationFailure.TLS);\n   r=s.executeQuery("SELECT NOT rolsuper AND NOT rolcreatedb AND NOT rolcreaterole AND NOT rolreplication AND NOT rolbypassrls AND rolconnlimit=2 FROM pg_roles WHERE rolname=current_user");if(!r.next()||!r.getBoolean(1))reject(MigrationFailure.AUTHORIZATION);\n   r=s.executeQuery("SELECT NOT rolsuper AND NOT rolcreatedb AND NOT rolcreaterole AND NOT rolreplication AND NOT rolbypassrls AND rolconnlimit=6 AND NOT has_database_privilege(rolname,current_database(),\'CREATE\') AND NOT has_database_privilege(rolname,current_database(),\'TEMP\') AND NOT pg_has_role(rolname,\'cinenest_qualification_migrator\',\'MEMBER\') FROM pg_roles WHERE rolname=\'cinenest_qualification_runtime\'");if(!r.next()||!r.getBoolean(1))reject(MigrationFailure.AUTHORIZATION);\n   r=s.executeQuery("SELECT count(*) FROM pg_database d CROSS JOIN LATERAL aclexplode(d.datacl) a WHERE d.datname=current_database() AND a.grantee=0");r.next();if(r.getInt(1)!=0)reject(MigrationFailure.AUTHORIZATION);\n  }\n }\n static String schemaSignature(java.sql.Connection c)throws Exception{\n  var digest=java.security.MessageDigest.getInstance("SHA-256");\n  try(var s=c.createStatement()){\n   s.execute("SET LOCAL search_path=pg_catalog");s.execute("SET LOCAL TimeZone=\'UTC\'");\n   try(var r=s.executeQuery(EXPECTED_SCHEMA_QUERY)){\n    for(;r.next();)for(int i=1;i<=3;i++){byte[] b=r.getString(i).getBytes(java.nio.charset.StandardCharsets.UTF_8);digest.update((b.length+":").getBytes(java.nio.charset.StandardCharsets.UTF_8));digest.update(b);}\n   }\n  }\n  return hex(digest.digest());\n }\n static void verifyPublicInfrastructure(java.sql.Connection c)throws Exception{\n  // PostgreSQL public ownership and the existing provider administration edge are\n  // checked separately;provider-only UC is not an application permission.\n  try(var s=c.createStatement()){\n   var r=s.executeQuery("SELECT pg_get_userbyid(nspowner) FROM pg_namespace WHERE nspname=\'public\'");if(!r.next()||!r.getString(1).equals("pg_database_owner"))reject(MigrationFailure.SCHEMA_DRIFT);\n   r=s.executeQuery("SELECT CASE WHEN a.grantee=0 THEN \'PUBLIC\' ELSE pg_get_userbyid(a.grantee) END,a.privilege_type,a.is_grantable,pg_get_userbyid(a.grantor) FROM pg_namespace n CROSS JOIN LATERAL aclexplode(n.nspacl) a WHERE n.nspname=\'public\'");\n   int provider=0;\n   for(;r.next();){\n    String who=r.getString(1),permission=r.getString(2);\n    boolean valid=who.equals("pg_database_owner")&&(permission.equals("USAGE")||permission.equals("CREATE"))||who.equals("PUBLIC")&&permission.equals("USAGE")||who.equals("cinenest_qualification_runtime")&&permission.equals("USAGE")||who.equals("cloudsqlsuperuser")&&(permission.equals("USAGE")||permission.equals("CREATE"));\n    if(!valid||r.getBoolean(3)||!r.getString(4).equals("pg_database_owner"))reject(MigrationFailure.SCHEMA_DRIFT);\n    if(who.equals("cloudsqlsuperuser"))provider++;\n   }\n   if(provider!=0&&provider!=2)reject(MigrationFailure.SCHEMA_DRIFT);\n  }\n }\n static void verifyExactHistory(org.flywaydb.core.api.MigrationInfo[] resolved,java.sql.Connection c)throws Exception{\n  try(var s=c.createStatement();var r=s.executeQuery("SELECT installed_rank,version,type,script,checksum,success,description FROM public.flyway_schema_history ORDER BY installed_rank")){\n   var seen=new java.util.HashSet<String>();int rank=0;var expectedVersions=EXPECTED_MIGRATION_FILES.keySet().stream().map(name->name.substring(1,name.indexOf("__"))).sorted(java.util.Comparator.comparingInt(Integer::parseInt)).toList();\n   for(;r.next();){\n    rank++;String version=r.getString(2),script=r.getString(4);\n    if(rank>44||r.getInt(1)!=rank||version==null||!version.equals(expectedVersions.get(rank-1))||!seen.add(version)||!r.getString(3).equals("SQL")||!r.getBoolean(6)||!EXPECTED_MIGRATION_FILES.containsKey(script)||!script.startsWith("V"+version+"__")||r.getObject(5)==null)reject(MigrationFailure.HISTORY_RECORD);\n    var pinned=java.util.Arrays.stream(resolved).filter(item->script.equals(item.getScript())).findFirst().orElseThrow();if(!java.util.Objects.equals(r.getObject(5),pinned.getResolvedChecksum())||!r.getString(7).equals(pinned.getDescription()))reject(MigrationFailure.HISTORY_CHECKSUM);\n   }\n   if(rank!=44||!seen.contains("45"))reject(MigrationFailure.HISTORY_COUNT);\n  }\n }\n static org.flywaydb.core.api.MigrationInfo[] resolvedHistory(org.flywaydb.core.Flyway f)throws Exception{\n  try{f.validate();var info=f.info();var all=info.all();\n   if(all.length!=44||info.pending().length!=0||info.current()==null||!info.current().getVersion().toString().equals("45"))reject(MigrationFailure.HISTORY_COUNT);\n   for(var migration:all){if(migration.getState()!=org.flywaydb.core.api.MigrationState.SUCCESS||!EXPECTED_MIGRATION_FILES.containsKey(migration.getScript())||!migration.isChecksumMatching()||!migration.isDescriptionMatching()||!migration.isTypeMatching()||migration.getAppliedChecksum()==null||!migration.getAppliedChecksum().equals(migration.getResolvedChecksum()))reject(MigrationFailure.HISTORY_CHECKSUM);}\n   return all;\n  }catch(MigrationContractException failure){throw failure;}catch(org.flywaydb.core.api.FlywayException error){reject(MigrationFailure.FLYWAY_VALIDATION);return null;}\n }\n\n static MigrationReport qualifyMigration(org.flywaydb.core.Flyway f,java.nio.file.Path root,String expectedState)throws Exception{\n  verifyMigrationArtifacts(root);\n  if(!expectedState.equals("FRESH_EMPTY")&&!expectedState.equals("RETAINED_CANDIDATE"))reject(MigrationFailure.UNEXPECTED_STATE);\n  if(!EXPECTED_SCHEMA_SHA256.matches("[0-9a-f]{64}"))reject(MigrationFailure.SCHEMA_EXPECTATION);\n  boolean hasHistory;\n  stage("MIGRATION_CLASSIFY","SQL_READ","44_V45");\n  try(var c=f.getConfiguration().getDataSource().getConnection();var s=c.createStatement()){\n   verifyMigrationIdentity(c);var r=s.executeQuery("SELECT to_regclass(\'public.flyway_schema_history\') IS NOT NULL");r.next();hasHistory=r.getBoolean(1);\n   if(!hasHistory){\n    if(!expectedState.equals("FRESH_EMPTY"))reject(MigrationFailure.UNEXPECTED_STATE);\n    r=s.executeQuery("SELECT count(*) FROM pg_namespace WHERE nspname !~ \'^pg_\' AND nspname NOT IN (\'information_schema\',\'public\')");r.next();if(r.getInt(1)!=0)reject(MigrationFailure.NONEMPTY_FRESH);\n    r=s.executeQuery("SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname=\'public\' AND c.relkind IN (\'r\',\'p\',\'v\',\'m\',\'S\',\'f\',\'c\')");r.next();if(r.getInt(1)!=0)reject(MigrationFailure.NONEMPTY_FRESH);\n    r=s.executeQuery("SELECT count(*) FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname=\'public\'");r.next();if(r.getInt(1)!=0)reject(MigrationFailure.NONEMPTY_FRESH);\n   }else if(!expectedState.equals("RETAINED_CANDIDATE"))reject(MigrationFailure.UNEXPECTED_STATE);\n  }\n  int executed=0;\n  if(!hasHistory){\n   stage("MIGRATION_FRESH_EXECUTE","SQL_MIGRATE","FRESH_AUTHORIZED");\n   var postPath=java.nio.file.Path.of(System.getProperty("cinenest.postMigrationSql","/app/sql/02-post-migration-runtime-grants.sql.proposed"));\n   if(java.nio.file.Files.isSymbolicLink(postPath)||!java.nio.file.Files.isRegularFile(postPath))reject(MigrationFailure.ARTIFACTS);\n   byte[] postBytes=java.nio.file.Files.readAllBytes(postPath);\n   if(!hex(java.security.MessageDigest.getInstance("SHA-256").digest(postBytes)).equals("52c43896824718533d5ecd113d2f1016f3009e8260927278a70edb836ce8eb52"))reject(MigrationFailure.ARTIFACTS);\n   executed=f.migrate().migrationsExecuted;if(executed!=44)reject(MigrationFailure.FRESH_COUNT);\n   try(var c=f.getConfiguration().getDataSource().getConnection();var s=c.createStatement()){s.execute(new String(postBytes,java.nio.charset.StandardCharsets.UTF_8));}\n  }\n  stage("MIGRATION_VALIDATE","SQL_READ","44_V45");\n  String schema;\n  var resolved=resolvedHistory(f);\n  try(var c=f.getConfiguration().getDataSource().getConnection()){\n   c.setReadOnly(true);c.setTransactionIsolation(java.sql.Connection.TRANSACTION_REPEATABLE_READ);c.setAutoCommit(false);\n   try{\n    verifyMigrationIdentity(c);verifyExactHistory(resolved,c);verifyPublicInfrastructure(c);\n    stage("MIGRATION_SCHEMA","SQL_READ","SCHEMA_MATCH");schema=schemaSignature(c);\n    if(!schema.equals(EXPECTED_SCHEMA_SHA256))reject(MigrationFailure.SCHEMA_DRIFT);\n   }finally{c.rollback();}\n  }\n  return new MigrationReport(hasHistory?"RETAINED_HISTORY_VERIFIED":"FRESH_HISTORY_VERIFIED",executed,44,schema);\n }\n}\n'
JAVA_SOURCE = _RETAINED_JAVA_TEMPLATE.replace("__SCHEMA_SHA__",RETAINED_SCHEMA_SHA256)

_PREDECESSOR_RETAINED_JOB_DOCUMENT=job_document

def job_document(mode,image,versions,service_url=None):
    document=_PREDECESSOR_RETAINED_JOB_DOCUMENT(mode,image,versions,service_url)
    if mode=='MIGRATE':
        container=document['spec']['template']['spec']['template']['spec']['containers'][0]
        container.setdefault('env',[]).append({'name':'CINENEST_MIGRATION_EXPECTED_STATE','value':'RETAINED_CANDIDATE'})
    return document

_PREDECESSOR_RETAINED_ARTIFACTS=monitoring_cleanup_artifacts

def monitoring_cleanup_artifacts(controller,helpers,private_helper):
    result=_PREDECESSOR_RETAINED_ARTIFACTS(controller,helpers,private_helper)
    text=result['cloud-session.py'];old="if mode=='MIGRATE':state['migration']='PASS_44_V45';save()"
    new="if mode=='MIGRATE':state['migration']='PASS_RETAINED_HISTORY_VERIFIED_44_V45';save()"
    if text.count(old)!=1:raise ValueError('Retained migration result boundary drift')
    text=text.replace(old,new,1).replace("v['probe_diagnostics_errors']()","v['retained_flyway_errors']()")
    compile(text,'cloud-session.py','exec');result['cloud-session.py']=text
    return result


RETAINED_LOCAL_DRIVER_SOURCE = 'import java.sql.*;\nimport org.flywaydb.core.Flyway;\npublic class LocalQualificationDriver {\n static String url=System.getenv("CINENEST_LOCAL_DB_URL"),user="cinenest_qualification_migrator",password=System.getenv("CINENEST_LOCAL_DB_PASSWORD");\n static Flyway flyway(String root){return Flyway.configure().dataSource(url,user,password).locations("filesystem:"+root).cleanDisabled(true).baselineOnMigrate(false).outOfOrder(false).ignoreMigrationPatterns(new String[0]).target("45").load();}\n static void localOnly(){\n  if(!"SYNTHETIC_TEST_ONLY".equals(System.getenv("CINENEST_LOCAL_QUALIFICATION"))||url==null||!(url.startsWith("jdbc:postgresql://127.0.0.1:")||url.matches("jdbc:postgresql://cinenest-retained-pg-[a-f0-9]{12}:5432/.*"))||!url.contains("sslmode=verify-full&sslrootcert="))throw new IllegalArgumentException();\n }\n static String digestData()throws Exception{\n  var digest=java.security.MessageDigest.getInstance("SHA-256");\n  try(var c=DriverManager.getConnection(url,"postgres",System.getenv("CINENEST_LOCAL_ADMIN_PASSWORD"));var st=c.createStatement()){\n   c.setReadOnly(true);c.setAutoCommit(false);\n   var rs=st.executeQuery("SELECT n.nspname,c.relname FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE c.relkind=\'r\' AND (n.nspname LIKE \'cinenest_%\' OR n.nspname=\'public\') ORDER BY n.nspname,c.relname");var names=new java.util.ArrayList<String>();\n   while(rs.next())names.add(\'"\'+rs.getString(1).replace("\\"","\\"\\"")+"\\".\\""+rs.getString(2).replace("\\"","\\"\\"")+\'"\');rs.close();\n   for(String name:names){digest.update(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));try(var rows=st.executeQuery("SELECT row_to_json(x)::text FROM "+name+" x ORDER BY row_to_json(x)::text")){while(rows.next())digest.update(rows.getString(1).getBytes(java.nio.charset.StandardCharsets.UTF_8));}}\n   c.rollback();\n  }\n  return java.util.HexFormat.of().formatHex(digest.digest());\n }\n public static void main(String[] args){\n  try{\n   localOnly();var root=java.nio.file.Path.of(args[1]);CinenestConnectivityProbe.verifyMigrationArtifacts(root);var f=flyway(args[1]);\n   if(args[0].equals("BASELINE")){\n    // Dedicated trusted disposable calibration,not present in the production image.\n    if(f.migrate().migrationsExecuted!=44)throw new IllegalStateException();f.validate();\n    var sql=java.nio.file.Path.of(System.getProperty("cinenest.postMigrationSql"));\n    if(!CinenestConnectivityProbe.hex(java.security.MessageDigest.getInstance("SHA-256").digest(java.nio.file.Files.readAllBytes(sql))).equals("52c43896824718533d5ecd113d2f1016f3009e8260927278a70edb836ce8eb52"))throw new IllegalStateException();\n    try(var c=DriverManager.getConnection(url,user,password);var st=c.createStatement()){st.execute(java.nio.file.Files.readString(sql));}\n    var resolved=CinenestConnectivityProbe.resolvedHistory(f);\n    try(var c=DriverManager.getConnection(url,user,password)){c.setReadOnly(true);c.setAutoCommit(false);CinenestConnectivityProbe.verifyMigrationIdentity(c);CinenestConnectivityProbe.verifyPublicInfrastructure(c);CinenestConnectivityProbe.verifyExactHistory(resolved,c);System.out.println("BASELINE_SCHEMA_SHA256="+CinenestConnectivityProbe.schemaSignature(c));c.rollback();}\n   }else if(args[0].equals("DIGEST")){System.out.println("DATA_SHA256="+digestData());}\n   else{\n    String expected=args[0];if(expected.equals("RETAINED_NO_POSTSQL")){System.setProperty("cinenest.postMigrationSql","/nonexistent/must_not_execute.sql");expected="RETAINED_CANDIDATE";}if(expected.equals("FRESH_BAD_POSTSQL")){System.setProperty("cinenest.postMigrationSql","/nonexistent/must_not_execute.sql");expected="FRESH_EMPTY";}\n    var report=CinenestConnectivityProbe.qualifyMigration(f,root,expected);System.out.println("RESULT="+report.state()+";NEW="+report.newMigrations()+";HISTORY="+report.historyRows()+";SCHEMA="+report.schemaSha256());\n   }\n  }catch(Throwable failure){\n   String code=failure instanceof CinenestConnectivityProbe.MigrationContractException m?m.reason.name():failure instanceof SQLException sql?"SQL_"+sql.getSQLState():failure.getClass().getSimpleName();\n   failure.printStackTrace();System.err.println("LOCAL_QUALIFICATION_FAILURE="+code);System.exit(1);\n  }\n }\n}\n'

def retained_build_image(evidence,task,classes,tag,nonce,env,deadline):
    import subprocess,time,tarfile,io,copy
    E=Path(evidence);state=json.loads((E/'local-session.json').read_text())
    continuing=state.get('imageBuildStarted',False)
    if continuing and not(state.get('imageAssemblyContinuation') and (task/'base-image.tar').is_file() and fingerprint(task/'base-image.tar')==state['imageAssemblyContinuation']['archiveSha256']):raise ValueError('The one diagnostic build is already consumed')
    if time.time()>deadline-300:raise TimeoutError('Insufficient image build/cleanup time')
    proof=json.loads((E/'build-resource-proof.json').read_text());actual=proof['actual']
    if not retained_resource_limits(actual['cpu.max'],actual['memory.max'],actual['memory.swap.max'],actual['pids.max']):raise ValueError('Actual supervised image-assembler bounds unavailable')
    state['imageBuildStarted']=True;(E/'local-session.json').write_text(json.dumps(state,indent=2)+'\n')
    def command(args,timeout=90):
        q=subprocess.run(['docker',*args],env=env,capture_output=True,text=True,timeout=min(timeout,max(1,deadline-time.time()-180)))
        if q.returncode:raise RuntimeError('Local image operation failed')
        return q.stdout
    existing=subprocess.run(['docker','image','inspect',tag],env=env,capture_output=True,text=True,timeout=15)
    if existing.returncode==0:raise ValueError('Refuse to overwrite an existing image tag')
    base=json.loads(command(['image','inspect',JRE]))[0]
    if base['Id']!='sha256:b7528962e65d43064d8a8b67ba175367896bc948363ea2d3fcd7f10d34e1f048':raise ValueError('Pinned runtime base changed')
    archive=task/'base-image.tar'
    if not continuing:
        q=subprocess.run(['docker','image','save','--output',str(archive),JRE],env=env,capture_output=True,text=True,timeout=120)
        if q.returncode:raise RuntimeError('Existing pinned image export failed')
    inputs={};cache=next(p for p in [ROOT/'.cache/m2',Path.home()/'.m2/repository'] if all((p/n).is_file() for n in DEPENDENCIES))
    for path in sorted(classes.glob('CinenestConnectivityProbe*.class')):
        data=path.read_bytes()
        if data[:4]!=bytes.fromhex('cafebabe') or int.from_bytes(data[6:8],'big')!=69:raise ValueError('Java25 bytecode required')
        inputs['app/classes/'+path.name]=data
    if 'app/classes/CinenestConnectivityProbe.class' not in inputs:raise ValueError('Qualified diagnostic class missing')
    for name,digest in DEPENDENCIES.items():
        path=cache/name
        if fingerprint(path)!=digest:raise ValueError('Dependency checksum changed')
        inputs['app/lib/'+path.name]=path.read_bytes()
    for name,digest in migration_inventory().items():
        path=ROOT/'backend/src/main/resources/db/migration'/name
        if fingerprint(path)!=digest:raise ValueError('Migration checksum changed')
        inputs['app/migrations/'+name]=path.read_bytes()
    sql=E.parent/'sql-staging-token-safety-20261122/next-cloud-monitoring-cleanup-20261126.proposed/sql-inputs/02-post-migration-runtime-grants.sql.proposed'
    if fingerprint(sql)!=SQL00_ACTOR_FILES[sql.name]:raise ValueError('Approved SQL changed')
    inputs['app/sql/'+sql.name]=sql.read_bytes()
    if any('LocalQualificationDriver' in name or '.env' in name or 'root.crt' in name for name in inputs):raise ValueError('Test fixture leakage refused')
    layer=io.BytesIO()
    with tarfile.open(fileobj=layer,mode='w') as output:
        for name,data in sorted(inputs.items()):
            member=tarfile.TarInfo(name);member.size=len(data);member.mode=0o644;member.uid=10001;member.gid=10001;member.mtime=0;output.addfile(member,io.BytesIO(data))
    layerbytes=layer.getvalue();layerhash=hashlib.sha256(layerbytes).hexdigest();production={name:hashlib.sha256(data).hexdigest() for name,data in inputs.items()};source_sha=hashlib.sha256(__import__('runpy').run_path(str(ROOT/'scripts/verify-cloud-connectivity.py'))['JAVA_SOURCE'].encode()).hexdigest()
    result_archive=task/'diagnostic-qualified-image.tar'
    with tarfile.open(archive,'r') as old,tarfile.open(result_archive,'w') as new:
        manifests=json.load(old.extractfile('manifest.json'))
        if len(manifests)!=1:raise ValueError('Single pinned base archive required')
        manifest=manifests[0];config=json.load(old.extractfile(manifest['Config']))
        # Preserve exact base config bytes/layers;append only the reviewed layer.
        retained_verify_oci_base(old,base,manifest)
        for member in old.getmembers():
            if member.name in ['manifest.json','repositories','index.json','oci-layout']:continue
            new.addfile(member,old.extractfile(member) if member.isfile() else None)
        config['config']={**config.get('config',{}),'User':'10001:10001','WorkingDir':'/app','Entrypoint':['java','-Xmx256m','-cp','/app/classes:/app/lib/*','CinenestConnectivityProbe'],'Cmd':['PROBE'],'Labels':{'com.cinenest.retained-qualification':nonce,'com.cinenest.diagnostic.java.sha256':source_sha,'com.cinenest.diagnostic.schema.sha256':RETAINED_SCHEMA_SHA256,'com.cinenest.diagnostic.artifacts.sha256':hashlib.sha256(json.dumps(production,sort_keys=True).encode()).hexdigest()}}
        config['rootfs']['diff_ids'].append('sha256:'+layerhash);config.setdefault('history',[]).append({'created_by':'CineNest reviewed offline artifact assembler','comment':'No RUN steps;no credentials/test fixtures'})
        data=json.dumps(config,separators=(',',':')).encode();config_hash=hashlib.sha256(data).hexdigest();config_name=config_hash+'.json';layer_name=layerhash+'/layer.tar'
        new_manifest=[{'Config':config_name,'RepoTags':[tag],'Layers':manifest['Layers']+[layer_name]}]
        for name,payload in [(layer_name,layerbytes),(config_name,data),('manifest.json',json.dumps(new_manifest).encode())]:
            member=tarfile.TarInfo(name);member.size=len(payload);member.mode=0o644;new.addfile(member,io.BytesIO(payload))
    command(['image','load','--input',str(result_archive)],120)
    image=json.loads(command(['image','inspect',tag]))[0]
    if not image['Id'].startswith('sha256:') or image['Config']['Labels']!=config['config']['Labels'] or image['Config']['User']!='10001:10001' or image['RootFS']['Layers']!=base['RootFS']['Layers']+['sha256:'+layerhash]:raise ValueError('Local image content/base verification failed')
    (E/'image-content-proof.json').write_text(json.dumps({'tag':tag,'id':image['Id'],'pinnedBase':base['Id'],'javaSourceSha256':source_sha,'schemaSha256':RETAINED_SCHEMA_SHA256,'compiledFiles':production,'compiler':'EXISTING_LOCAL_JDK25_RELEASE25_SUPERVISED_CGROUP','classFileMajor':69,'fixturesBundled':False,'credentialsBundled':False,'runtimeUser':'10001:10001','buildResourceProof':'build-resource-proof.json','cloudPublication':'NOT_RUN_NOT_AUTHORIZED'},indent=2)+'\n')
    archive.unlink();result_archive.unlink()


def retained_parent_cleanup(evidence):
    """Independent task-ID cleanup even if the bounded worker terminates early."""
    import subprocess,time
    E=Path(evidence);state=json.loads((E/'local-session.json').read_text());nonce=state['nonce'];env=dict(os.environ,DOCKER_HOST='unix:///var/run/docker.sock');events=[]
    task=E/('local-runtime-'+nonce);owned=state.get('ownedContainers',{}).copy()
    for p in task.glob('*.cid'):
        cid=p.read_text().strip()
        if re.fullmatch('[a-f0-9]{64}',cid):owned.setdefault('cinenest-retained-client-'+nonce+'-'+cid[:12],cid)
    for name,cid in owned.items():
        q=subprocess.run(['docker','inspect',cid],env=env,capture_output=True,text=True,timeout=10)
        if q.returncode:events.append({'kind':'container','name':name,'status':'ABSENCE_VERIFIED'});continue
        row=json.loads(q.stdout)[0]
        if row['Id']!=cid or row['Config'].get('Labels',{}).get('com.cinenest.retained-qualification')!=nonce:
            events.append({'kind':'container','name':name,'status':'IDENTITY_DRIFT_NOT_REMOVED'});continue
        q=subprocess.run(['docker','rm','-f',cid],env=env,capture_output=True,text=True,timeout=20);events.append({'kind':'container','name':name,'status':'REMOVED_OWNED' if q.returncode==0 else 'ERROR'})
    network=state.get('ownedNetwork')
    if network:
        q=subprocess.run(['docker','network','inspect',network['id']],env=env,capture_output=True,text=True,timeout=10)
        if q.returncode:events.append({'kind':'network','status':'ABSENCE_VERIFIED'})
        else:
            row=json.loads(q.stdout)[0]
            if row['Id']==network['id'] and row.get('Labels',{}).get('com.cinenest.retained-qualification')==nonce and not row.get('Containers'):
                q=subprocess.run(['docker','network','rm',network['id']],env=env,capture_output=True,text=True,timeout=20);events.append({'kind':'network','status':'REMOVED_OWNED' if q.returncode==0 else 'ERROR'})
            else:events.append({'kind':'network','status':'IDENTITY_OR_ATTACHMENT_DRIFT_NOT_REMOVED'})
    for name in ['database.env','client.env','root.key','server.key']:
        path=task/name
        if path.is_file() and not path.is_symlink():path.unlink()
    after={}
    for kind,args in [('containers',['ps','-aq']),('volumes',['volume','ls','-q']),('networks',['network','ls','-q'])]:
        q=subprocess.run(['docker',*args],env=env,capture_output=True,text=True,timeout=10)
        after[kind]=sorted(q.stdout.strip().splitlines()) if q.returncode==0 else ['INVENTORY_UNAVAILABLE']
    result={'events':events,'after':after,'before':state.get('beforeInventory'),'inventoryPreserved':state.get('beforeInventory')==after,'elapsedSeconds':round(time.time()-state['startedEpoch'],2),'within30Minutes':time.time()-state['startedEpoch']<=1800,'credentialsPersisted':any((task/n).exists() for n in ['database.env','client.env','root.key','server.key'])}
    (E/'independent-local-cleanup.json').write_text(json.dumps(result,indent=2)+'\n')
    if not result['inventoryPreserved'] or not result['within30Minutes'] or result['credentialsPersisted']:raise RuntimeError('Independent local cleanup not verified')


def retained_resource_limits(cpu,memory,swap,pids):
    try:
        quota,period=cpu.split()
        return (quota!='max' and 0<int(quota)<=int(period) and int(memory)<=1024**3
                and int(memory)>0 and int(swap)==0 and 0<int(pids)<=128)
    except (ValueError,TypeError,AttributeError):return False


def retained_qualification_receipt(evidence,now=None,allow_failed_resume=False):
    import time
    root=Path(evidence)
    if not root.is_absolute() or root.is_symlink():raise ValueError('Absolute owned evidence directory required')
    request=root/'source-admission.requested.json';owner=root/'owner-approval.json'
    if fingerprint(request)!=RETAINED_FLYWAY_APPROVAL_SHA256:raise ValueError('Exact retained-history Owner request required')
    approval=json.loads(owner.read_text())
    if (approval.get('source')!='Owner answer call_f541ecd9e60247dd9e87b82d008c1792: Approve exact source and local qualification'
            or approval.get('requestSha256')!=RETAINED_FLYWAY_APPROVAL_SHA256 or approval.get('newLocalSessions')!=1
            or approval.get('maximumLocalSecondsIncludingCleanup')!=1800 or approval.get('cloudSessions')!=0):raise ValueError('Local-only Owner receipt mismatch')
    current=json.loads((ROOT/'knowledge/execution/change-sets.json').read_text())['cloudRunRetainedFlywayHistory']
    if current['status']!='READY' or current['authority']!=approval:raise ValueError('Retained-history source not admitted')
    if not re.fullmatch(r'[1-9][0-9]*_PASS_0_FAILURE_0_ERROR_0_SKIPPED',current['qualification'].get('offlineTests','')):raise ValueError('Offline source qualification required before local runtime')
    now=time.time() if now is None else now
    marker=root/'local-session.json'
    if marker.exists():
        state=json.loads(marker.read_text())
        if state['requestSha256']!=RETAINED_FLYWAY_APPROVAL_SHA256 or not 0<=now-state['startedEpoch']<retained_local_seconds(root)-120:raise ValueError('Local session consumed or expired;no new window inferred')
        if state.get('completed') and not(allow_failed_resume and state.get('qualification')=='FAIL' and state.get('inventoryPreserved') is True):raise ValueError('Completed successful/unsafe local session cannot be resumed')
    return approval


def retained_local_worker(evidence):
    import subprocess,time,uuid,signal,stat,tarfile,io,tempfile,copy
    E=Path(evidence);retained_qualification_receipt(E);state=json.loads((E/'local-session.json').read_text());nonce=state['nonce'];deadline=state['startedEpoch']+retained_local_seconds(E)
    if not re.fullmatch('[a-f0-9]{12}',nonce):raise ValueError('Task nonce unavailable')
    def save():
        (E/'local-session.json').write_text(json.dumps(state,indent=2)+'\n')
    # The native compiler/image assembler is actually bounded,not merely declared.
    cg=Path('/sys/fs/cgroup')/next(x.split(':',2)[2].lstrip('/') for x in Path('/proc/self/cgroup').read_text().splitlines() if x.startswith('0::'))
    if not str(cg).endswith('cinenest-retained-qualification-'+nonce+'.service'):raise ValueError('Exact task worker cgroup required')
    controls={n:(cg/n).read_text().strip() for n in ['cpu.max','memory.max','memory.swap.max','pids.max']}
    if not retained_resource_limits(controls['cpu.max'],controls['memory.max'],controls['memory.swap.max'],controls['pids.max']):raise ValueError('Build/qualification worker cgroup limits not enforced')
    (E/'build-resource-proof.json').write_text(json.dumps({'cgroup':str(cg),'actual':controls,'status':'PASS'},indent=2)+'\n')
    task=E/('local-runtime-'+nonce);task.mkdir(mode=0o700,exist_ok=True);public=task/'public';public.mkdir(mode=0o755,exist_ok=True);public.chmod(0o755)
    env=dict(os.environ,DOCKER_HOST='unix:///var/run/docker.sock');state.setdefault('ownedContainers',{});state.setdefault('groups',[]);state.setdefault('attempts',0);state['attempts']+=1;save()
    def run(args,timeout=90,input=None,extra_env=None,reserve=180):
        if time.time()>=deadline-reserve:raise TimeoutError('Local work deadline reached;cleanup reserve preserved')
        result=subprocess.run(args,input=input,text=True,capture_output=True,env=env if extra_env is None else dict(env,**extra_env),timeout=max(1,min(timeout,deadline-time.time()-reserve)))
        return result
    def docker(*args,timeout=90,input=None):
        q=run(['docker',*args],timeout,input)
        if q.returncode:raise RuntimeError('Owned Docker operation failed;details kept private')
        return q.stdout.strip()
    def inventory(cleanup=False):
        if cleanup:
            def read(*a):
                q=subprocess.run(["docker",*a],env=env,capture_output=True,text=True,timeout=15)
                if q.returncode:raise RuntimeError("Final inventory unavailable")
                return sorted(q.stdout.strip().splitlines())
            return {"containers":read("ps","-aq"),"volumes":read("volume","ls","-q"),"networks":read("network","ls","-q")}
        return {'containers':sorted(docker('ps','-aq').splitlines()),'volumes':sorted(docker('volume','ls','-q').splitlines()),'networks':sorted(docker('network','ls','-q').splitlines())}
    if 'beforeInventory' not in state:state['beforeInventory']=inventory();save()
    pg='cinenest-retained-pg-'+nonce;network='cinenest-retained-net-'+nonce;tag='cinenest-retained-diagnostics:qualification-'+nonce;label='com.cinenest.retained-qualification='+nonce
    migrations=ROOT/'backend/src/main/resources/db/migration';sqlroot=E.parent/'sql-staging-token-safety-20261122/next-cloud-monitoring-cleanup-20261126.proposed/sql-inputs';cache=next((p for p in [ROOT/'.cache/m2',Path.home()/'.m2/repository'] if all((p/n).is_file() for n in DEPENDENCIES)),None)
    if cache is None:raise ValueError('Existing pinned dependency cache unavailable')
    for n,h in DEPENDENCIES.items():
        if fingerprint(cache/n)!=h:raise ValueError('Pinned dependency mismatch')
    require_sql00_actor_manifest({n:fingerprint(sqlroot/n) for n in SQL00_ACTOR_FILES});migration_inventory()
    pgimage='postgres:18.6@sha256:74935e72241653ca55e0414067e6d8763aceb8a810eb51b452253ec3dcfc4336'
    for image in [pgimage,JRE]:docker('image','inspect',image)
    password='SYNTHETIC_TEST_ONLY_'+uuid.uuid4().hex;admin='SYNTHETIC_TEST_ONLY_'+uuid.uuid4().hex
    secret=task/'database.env';secret.write_text('POSTGRES_PASSWORD='+admin+'\n');secret.chmod(0o600)
    ca=task/'root.crt';cakey=task/'root.key';serverkey=task/'server.key';servercsr=task/'server.csr';servercrt=task/'server.crt';extensions=task/'server.ext'
    extensions.write_text('subjectAltName=DNS:'+pg+',IP:127.0.0.1\nextendedKeyUsage=serverAuth\n')
    for args in [
        ['openssl','req','-x509','-newkey','rsa:2048','-nodes','-sha256','-days','1','-subj','/CN=CineNest SYNTHETIC_TEST_ONLY local CA','-keyout',str(cakey),'-out',str(ca)],
        ['openssl','req','-newkey','rsa:2048','-nodes','-sha256','-subj','/CN='+pg,'-keyout',str(serverkey),'-out',str(servercsr)],
        ['openssl','x509','-req','-in',str(servercsr),'-CA',str(ca),'-CAkey',str(cakey),'-CAcreateserial','-days','1','-sha256','-extfile',str(extensions),'-out',str(servercrt)]]:
        if run(args).returncode:raise RuntimeError('Synthetic TLS fixture generation failed')
    cakey.chmod(0o600);serverkey.chmod(0o600);shutil.copyfile(ca,public/'root.crt');(public/'root.crt').chmod(0o644)
    hba=task/'pg_hba.conf';hba.write_text('local all all trust\nhostnossl all all all reject\nhostssl all all all scram-sha-256\n');hba.chmod(0o644)
    cp=os.pathsep.join(str(cache/n) for n in DEPENDENCIES)
    classes=task/'classes';classes.mkdir(exist_ok=True);source=task/'CinenestConnectivityProbe.java';driver=task/'LocalQualificationDriver.java';driver.write_text(RETAINED_LOCAL_DRIVER_SOURCE)
    def compile_classes():
        mod=__import__('runpy').run_path(str(ROOT/'scripts/verify-cloud-connectivity.py'));source.write_text(mod['JAVA_SOURCE']);driver.write_text(mod['RETAINED_LOCAL_DRIVER_SOURCE'])
        javac=shutil.which('javac');q=run([javac,'-proc:none','--release','25','-J-Xmx256m','-J-XX:ActiveProcessorCount=1','-cp',cp,'-d',str(classes),str(source),str(driver)],120)
        if q.returncode:raise RuntimeError('Qualification Java compilation failed')
        return mod
    compile_classes()
    pgid=None;netid=None;failures=[];image_id=None
    def admin_sql(sql):
        if pgid is None:raise ValueError('Owned database not initialized')
        return docker('exec','-i',pgid,'psql','-X','-U','postgres','-d','postgres','-v','ON_ERROR_STOP=1','-At',input=sql)
    def create_empty():
        admin_sql("DROP DATABASE IF EXISTS cinenest_connectivity_qualification WITH(FORCE);CREATE DATABASE cinenest_connectivity_qualification OWNER cinenest_qualification_migrator ALLOW_CONNECTIONS false;BEGIN;REVOKE ALL ON DATABASE cinenest_connectivity_qualification FROM PUBLIC;GRANT CONNECT ON DATABASE cinenest_connectivity_qualification TO cinenest_qualification_runtime;ALTER DATABASE cinenest_connectivity_qualification ALLOW_CONNECTIONS true;COMMIT;")
    def restore():
        admin_sql("DROP DATABASE cinenest_connectivity_qualification WITH(FORCE);CREATE DATABASE cinenest_connectivity_qualification WITH TEMPLATE cinenest_retained_template OWNER cinenest_qualification_migrator STRATEGY FILE_COPY ALLOW_CONNECTIONS false;BEGIN;REVOKE ALL ON DATABASE cinenest_connectivity_qualification FROM PUBLIC;GRANT CONNECT ON DATABASE cinenest_connectivity_qualification TO cinenest_qualification_runtime;ALTER DATABASE cinenest_connectivity_qualification ALLOW_CONNECTIONS true;COMMIT;")
    def mutate(sql):return docker('exec','-i',pgid,'psql','-X','-U','postgres','-d','cinenest_connectivity_qualification','-v','ON_ERROR_STOP=1','-At',input=sql)
    url=None
    java_counter=0
    runtime_lib=task/'lib';runtime_lib.mkdir(exist_ok=True);runtime_lib.chmod(0o755)
    runtime_sql=task/'sql';runtime_sql.mkdir(exist_ok=True);runtime_sql.chmod(0o755)
    for dependency,digest in DEPENDENCIES.items():
        target=runtime_lib/Path(dependency).name;shutil.copyfile(cache/dependency,target);target.chmod(0o644)
    name='02-post-migration-runtime-grants.sql.proposed';shutil.copyfile(sqlroot/name,runtime_sql/name);(runtime_sql/name).chmod(0o644)
    def java(mode,initial=None):
        nonlocal java_counter
        java_counter+=1;client_nonce=uuid.uuid4().hex[:12];name='cinenest-retained-java-'+nonce+'-'+client_nonce;cidfile=task/('java-'+client_nonce+'.cid')
        clientenv=task/'client.env';clientenv.write_text('CINENEST_LOCAL_DB_URL='+url+'\nCINENEST_LOCAL_DB_PASSWORD='+password+'\nCINENEST_LOCAL_ADMIN_PASSWORD='+admin+'\nCINENEST_LOCAL_QUALIFICATION=SYNTHETIC_TEST_ONLY\n');clientenv.chmod(0o600)
        command=['docker','run','--rm','--name',name,'--cidfile',str(cidfile),'--label',label,'--network',network,'--user','10001:10001','--cpus','1','--memory','1g','--memory-swap','1g','--pids-limit','128','--read-only','--security-opt','no-new-privileges','--cap-drop','ALL','--tmpfs','/tmp:rw,size=16m','--env-file',str(clientenv)]
        for source,target in [(public,'/qualification'),(classes,'/qualification-classes'),(runtime_lib,'/qualification-lib'),(runtime_sql,'/qualification-sql')]:command+=['--mount','type=bind,src='+str(source)+',dst='+target+',readonly']
        command+=['--entrypoint','java',JRE,'-Xmx256m','-XX:ActiveProcessorCount=1','-Dcinenest.postMigrationSql=/qualification-sql/02-post-migration-runtime-grants.sql.proposed','-cp','/qualification-classes:/qualification-lib/*','LocalQualificationDriver',mode,'/app/migrations']
        command[-1]='/qualification-migrations'
        command[command.index('--entrypoint'):command.index('--entrypoint')]=['--mount','type=bind,src='+str(migrations)+',dst=/qualification-migrations,readonly']
        try:q=run(command,180)
        finally:
            if cidfile.exists():state['ownedContainers'][name]=cidfile.read_text().strip();save()
        log=task/('java-'+str(time.time_ns())+'.log');log.write_text(q.stdout+q.stderr);log.chmod(0o600)
        return q
    cases={}
    def correction_wait(phase):
        state['phase']='WAITING_SOURCE_CORRECTION';state['failedPhase']=phase;save();control=E/'local-correction.json'
        while time.time()<deadline-300:
            if control.exists():
                data=json.loads(control.read_text());control.unlink()
                if set(data)!={'action','sourceSha256'} or data['action']!='RETEST' or data['sourceSha256']!=fingerprint(ROOT/'scripts/verify-cloud-connectivity.py'):raise ValueError('Exact authorized source correction signal required')
                compile_classes();state['phase']='QUALIFYING_SAME_LOCAL_SESSION';save();return
            time.sleep(1)
        raise TimeoutError('No qualified correction within original local window')
    def group(name,fn):
        cases[name]=fn
        try:fn();state['groups'].append({'name':name,'status':'PASS','evidence':'REAL_POSTGRESQL_18_6_JDBC_VERIFY_FULL'})
        except Exception as error:failures.append(name);state['groups'].append({'name':name,'status':'FAIL','failureClass':type(error).__name__})
        save()
    def expect(result,success):
        if (result.returncode==0)!=success:raise AssertionError('Unexpected qualification outcome')
        return result
    def negative(name,sql):
        def test():
            restore();mutate(sql);before=expect(java('DIGEST'),True).stdout;expect(java('RETAINED_CANDIDATE'),False);after=expect(java('DIGEST'),True).stdout
            if before!=after:raise AssertionError('Failed verification mutated data/history')
        group(name,test)
    try:
        netid=docker('network','create','--internal','--label',label,network);state['ownedNetwork']={'name':network,'id':netid};save()
        wrapper='cp /qualification/server.key /var/lib/postgresql/qualification.key; cp /qualification/server.crt /var/lib/postgresql/qualification.crt; cp /qualification/pg_hba.conf /var/lib/postgresql/qualification.hba; chown postgres:postgres /var/lib/postgresql/qualification.key /var/lib/postgresql/qualification.crt /var/lib/postgresql/qualification.hba; chmod 600 /var/lib/postgresql/qualification.key; exec docker-entrypoint.sh postgres -c listen_addresses=0.0.0.0 -c ssl=on -c ssl_min_protocol_version=TLSv1.3 -c ssl_cert_file=/var/lib/postgresql/qualification.crt -c ssl_key_file=/var/lib/postgresql/qualification.key -c hba_file=/var/lib/postgresql/qualification.hba -c max_connections=32 -c shared_buffers=64MB -c max_wal_size=64MB -c min_wal_size=32MB -c checkpoint_timeout=30s -c log_min_error_statement=panic'
        pgid=docker('run','-d','--name',pg,'--label',label,'--network',network,'--cpus','1','--memory','1g','--memory-swap','1g','--pids-limit','128','--tmpfs','/var/lib/postgresql:rw,size=496m','--shm-size','16m','--env-file',str(secret),'-p','127.0.0.1::5432','--mount','type=bind,src='+str(task)+',dst=/qualification,readonly','--entrypoint','sh',pgimage,'-c',wrapper)
        state['ownedContainers'][pg]=pgid;save();ins=json.loads(docker('inspect',pgid))[0];host=ins['HostConfig'];assert host['NanoCpus']==10**9 and host['Memory']==1024**3 and host['MemorySwap']==1024**3 and host['PidsLimit']==128 and host['ShmSize']==16*1024**2 and host['Tmpfs']=={'/var/lib/postgresql':'rw,size=496m'}
        bindings=host.get('PortBindings',{}).get('5432/tcp',[]);assert bindings and all(x['HostIp']=='127.0.0.1' for x in bindings)
        state['postgresPortMetadata']={'requestedLoopback':bindings,'actual':ins['NetworkSettings'].get('Ports'),'internalNetwork':True};save()
        url='jdbc:postgresql://'+pg+':5432/cinenest_connectivity_qualification?sslmode=verify-full&sslrootcert=/qualification/root.crt'
        ready=False
        for attempt in range(120):
            q=run(['docker','exec',pgid,'pg_isready','-h','127.0.0.1','-U','postgres'],10)
            if q.returncode==0:ready=True;break
            time.sleep(0.25)
        if not ready:raise TimeoutError('Database process readiness failed')
        admin_sql("CREATE ROLE cinenest_qualification_runtime NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS CONNECTION LIMIT 6;CREATE ROLE cinenest_qualification_migrator NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS CONNECTION LIMIT 2;")
        admin_sql("ALTER ROLE cinenest_qualification_migrator LOGIN PASSWORD '"+password+"';")
        while True:
            create_empty();baseline=java('BASELINE');match=re.search(r'BASELINE_SCHEMA_SHA256=([0-9a-f]{64})',baseline.stdout)
            if baseline.returncode==0 and match:break
            state.setdefault('failedCalibrationAttempts',[]).append({'status':'FAIL','exit':baseline.returncode,'atEpoch':time.time()});save();correction_wait('TRUSTED_FRESH_CALIBRATION')
        digest=match[1];state['trustedFreshSchemaSha256']=digest;state['schemaExpectationProvenance']='SAME44IMMUTABLE_MIGRATIONS_AND_APPROVED_SQL02_ON_DISPOSABLE_POSTGRES18_6';save()
        sourcefile=ROOT/'scripts/verify-cloud-connectivity.py';text=sourcefile.read_text();pattern=r'^RETAINED_SCHEMA_SHA256 = "(?:PENDING_TRUSTED_LOCAL_BASELINE|[0-9a-f]{64})"$'
        if len(re.findall(pattern,text,re.M))!=1:raise ValueError('Schema expectation update boundary changed')
        text=re.sub(pattern,'RETAINED_SCHEMA_SHA256 = "'+digest+'"',text,flags=re.M);sourcefile.write_text(text);compile_classes()
        admin_sql("CREATE DATABASE cinenest_retained_template WITH TEMPLATE cinenest_connectivity_qualification OWNER cinenest_qualification_migrator STRATEGY FILE_COPY ALLOW_CONNECTIONS false;")
        def fresh():
            create_empty();q=expect(java('FRESH_EMPTY'),True)
            if 'RESULT=FRESH_HISTORY_VERIFIED;NEW=44;HISTORY=44;' not in q.stdout:raise AssertionError('Wrong fresh reporting')
        group('01_fresh44_and_schema',fresh)
        def retained():
            before=expect(java('DIGEST'),True).stdout;q=expect(java('RETAINED_CANDIDATE'),True);after=expect(java('DIGEST'),True).stdout
            if before!=after or 'RESULT=RETAINED_HISTORY_VERIFIED;NEW=0;HISTORY=44;' not in q.stdout:raise AssertionError('Retained history changed or inaccurate reporting')
        group('02_retained44_V45_zero_mutations',retained)
        group('03_safe_retained_retry',retained)
        negative('04_missing_migration',"DELETE FROM public.flyway_schema_history WHERE version='45';")
        negative('05_failed_migration',"UPDATE public.flyway_schema_history SET success=false WHERE version='45';")
        negative('06_wrong_checksum',"UPDATE public.flyway_schema_history SET checksum=checksum+1 WHERE version='1';")
        negative('07_unexpected_version',"UPDATE public.flyway_schema_history SET version='99',script='V99__unexpected.sql' WHERE version='45';")
        negative('08_duplicate_version',"UPDATE public.flyway_schema_history SET version='1',script='V1__foundation_namespace.sql' WHERE version='2';")
        negative('09_schema_drift',"CREATE TABLE public.unapproved_schema_drift(id integer);")
        negative('10_missing_object',"DROP TABLE cinenest_identity.account CASCADE;")
        negative('11_wrong_schema_owner',"ALTER SCHEMA cinenest_booking OWNER TO postgres;")
        negative('12_unauthorized_baseline_record',"UPDATE public.flyway_schema_history SET type='BASELINE' WHERE version='1';")
        negative('13_interrupted_partial_state',"DELETE FROM public.flyway_schema_history WHERE version IN ('44','45');CREATE TABLE public.interrupted_partial(id integer);")
        negative('14_wrong_description',"UPDATE public.flyway_schema_history SET description='unapproved description' WHERE version='1';")
        def wrong_database():
            nonlocal url
            old=url;url=url.replace('/cinenest_connectivity_qualification?','/postgres?')
            try:expect(java('RETAINED_CANDIDATE'),False)
            finally:url=old
        group('15_wrong_database',wrong_database)
        def unexpected_empty():
            create_empty();expect(java('RETAINED_CANDIDATE'),False);assert admin_sql("SELECT count(*) FROM pg_database WHERE datname='cinenest_connectivity_qualification';")=='1'
        group('16_retained_mode_never_migrates_empty',unexpected_empty)
        restore()
        def no_fresh_on_retained():expect(java('FRESH_EMPTY'),False)
        group('17_fresh_mode_refuses_retained',no_fresh_on_retained)
        def no_postsql_on_retained():expect(java('RETAINED_NO_POSTSQL'),True)
        group('18_retained_does_not_read_or_execute_post_sql',no_postsql_on_retained)
        def invalid_postsql_no_migration():
            create_empty();before=expect(java('DIGEST'),True).stdout;expect(java('FRESH_BAD_POSTSQL'),False);after=expect(java('DIGEST'),True).stdout
            if before!=after:raise AssertionError('Bad post-SQL artifact executed migration')
            restore()
        group('19_fresh_invalid_artifact_refused_before_migration',invalid_postsql_no_migration)
        while failures:
            failed=list(failures);state.setdefault('failedTestAttempts',[]).append({'groups':failed,'atEpoch':time.time()});save();correction_wait('POSTGRESQL_CASES');failures.clear()
            for name in failed:group(name,cases[name])
        state['postgresQualification']='PASS';save()
        # Local image build/import is separate from cloud publication. No Docker
        # build worker is required:the bounded native worker assembles a new layer.
        reloaded=__import__('runpy').run_path(str(ROOT/'scripts/verify-cloud-connectivity.py'));globals()['RETAINED_SCHEMA_SHA256']=reloaded['RETAINED_SCHEMA_SHA256']
        retained_build_image(E,task,classes,tag,nonce,env,deadline)
        reloaded=__import__('runpy').run_path(str(ROOT/'scripts/verify-cloud-connectivity.py'));globals()['RETAINED_SCHEMA_SHA256']=reloaded['RETAINED_SCHEMA_SHA256']
        state['imageBuildStarted']=True
        image_id=json.loads(docker('image','inspect',tag))[0]['Id'];state['retainedImage']={'tag':tag,'id':image_id};save()
        # Test fixtures are mounted read-only for this local assertion,never baked.
        shutil.copyfile(classes/'LocalQualificationDriver.class',public/'LocalQualificationDriver.class');(public/'LocalQualificationDriver.class').chmod(0o644)
        clientenv=task/'client.env';clientenv.write_text('CINENEST_LOCAL_DB_URL=jdbc:postgresql://'+pg+':5432/cinenest_connectivity_qualification?sslmode=verify-full&sslrootcert=/qualification/root.crt\nCINENEST_LOCAL_DB_PASSWORD='+password+'\nCINENEST_LOCAL_QUALIFICATION=SYNTHETIC_TEST_ONLY\n');clientenv.chmod(0o600)
        def image_retained():
            name='cinenest-retained-client-'+nonce;cidfile=task/'client.cid';q=run(['docker','run','--rm','--name',name,'--cidfile',str(cidfile),'--label',label,'--network',network,'--cpus','1','--memory','1g','--memory-swap','1g','--pids-limit','128','--read-only','--tmpfs','/tmp:rw,size=16m','--env-file',str(clientenv),'--mount','type=bind,src='+str(public)+',dst=/qualification,readonly','--entrypoint','java',tag,'-Xmx256m','-XX:ActiveProcessorCount=1','-cp','/app/classes:/app/lib/*:/qualification','LocalQualificationDriver','RETAINED_CANDIDATE','/app/migrations'],180)
            if cidfile.exists():state['ownedContainers'][name]=cidfile.read_text().strip();save()
            if q.returncode or 'RESULT=RETAINED_HISTORY_VERIFIED;NEW=0;HISTORY=44;' not in q.stdout:raise AssertionError('Actual diagnostic image retained validation failed')
        group('20_actual_image_retained_JRE_and_TLS',image_retained)
        if failures:raise AssertionError('Image qualification failed')
        state['qualification']='PASS';state['imageReadiness']='QUALIFIED_LOCAL_ONLY_NOT_PUBLISHED';save()
    except Exception as error:
        state['imageBuildStarted']=state.get('imageBuildStarted',False) or json.loads((E/'local-session.json').read_text()).get('imageBuildStarted',False)
        import traceback
        state['qualification']='FAIL';state['failureClass']=type(error).__name__;state['failureLocation']=[{'file':Path(x.filename).name,'line':x.lineno,'function':x.name} for x in traceback.extract_tb(error.__traceback__)];save()
    finally:
        state['cleanup']=[]
        for cidfile in task.glob('*.cid'):
            cid=cidfile.read_text().strip()
            if re.fullmatch('[a-f0-9]{64}',cid):state['ownedContainers'].setdefault('cinenest-retained-client-'+nonce+'-'+cid[:12],cid)
        for name,cid in list(state['ownedContainers'].items())[::-1]:
            q=subprocess.run(['docker','inspect',cid],env=env,capture_output=True,text=True,timeout=15)
            if q.returncode:state['cleanup'].append({'kind':'container','name':name,'status':'ABSENT'});continue
            info=json.loads(q.stdout)[0]
            if info['Id']!=cid or info['Config'].get('Labels',{}).get('com.cinenest.retained-qualification')!=nonce:
                state['cleanup'].append({'kind':'container','name':name,'status':'IDENTITY_MISMATCH_NOT_REMOVED'});continue
            q=subprocess.run(['docker','rm','-f',cid],env=env,capture_output=True,text=True,timeout=30);state['cleanup'].append({'kind':'container','name':name,'status':'PASS' if q.returncode==0 else 'ERROR'})
        if netid:
            q=subprocess.run(['docker','network','inspect',netid],env=env,capture_output=True,text=True,timeout=15)
            if q.returncode==0:
                info=json.loads(q.stdout)[0]
                if info['Id']==netid and info.get('Labels',{}).get('com.cinenest.retained-qualification')==nonce:
                    q=subprocess.run(['docker','network','rm',netid],env=env,capture_output=True,text=True,timeout=30);state['cleanup'].append({'kind':'network','status':'PASS' if q.returncode==0 else 'ERROR'})
        for path in [secret,cakey,serverkey,task/'client.env']:
            if path.is_file():path.unlink()
        state['afterInventory']=inventory(True);state['inventoryPreserved']=state['beforeInventory']==state['afterInventory'];state['completedEpoch']=time.time();state['elapsedSeconds']=round(time.time()-state['startedEpoch'],2);state['within30Minutes']=state['elapsedSeconds']<=1800;state['withinApprovedWindow']=retained_elapsed_within_budget(state['elapsedSeconds'],retained_local_seconds(E));state['completed']=True;save()
    return {'status':state.get('qualification','ERROR'),'postgres':state.get('postgresQualification','NOT_RUN'),'image':state.get('imageReadiness','NOT_RUN'),'groups':state['groups'],'cleanup':state['cleanup'],'inventoryPreserved':state['inventoryPreserved'],'within30Minutes':state['within30Minutes'],'withinApprovedWindow':state['withinApprovedWindow'],'cloudRun':'NOT_RUN_NOT_AUTHORIZED'}


def retained_local_launch(evidence,resume=False):
    import subprocess,time,uuid
    E=Path(evidence);retained_qualification_receipt(E,allow_failed_resume=resume)
    marker=E/'local-session.json'
    if marker.exists():
        if not resume:raise ValueError('One local session already launched;no fresh session inferred')
        state=json.loads(marker.read_text());previous=marker.read_bytes();snapshot=E/('local-session.failed-attempt-'+str(state.get('attempts',0))+'.json')
        with snapshot.open('xb') as f:f.write(previous)
        if time.time()>=state['startedEpoch']+retained_local_seconds(E)-300:raise TimeoutError('Original local window lacks correction/cleanup time')
        state['resumedWithinOriginalWindow']=True;state['completed']=False;state['ownedContainers']={};state.pop('ownedNetwork',None);state['groups']=[];state.pop('failureClass',None);state.pop('failureLocation',None);state.pop('qualification',None);marker.write_text(json.dumps(state,indent=2)+'\n')
    else:
        if resume:raise ValueError('Cannot invent a prior session')
        nonce=uuid.uuid4().hex[:12];state={'requestSha256':RETAINED_FLYWAY_APPROVAL_SHA256,'startedEpoch':time.time(),'nonce':nonce,'completed':False,'cloudSession':False}
        with marker.open('x') as f:json.dump(state,f,indent=2);f.write('\n')
    nonce=state['nonce'];remaining=int(state['startedEpoch']+retained_local_seconds(E)-time.time());maximum=min(1700,remaining-90)
    # RuntimeMaxSec=1700 is a ceiling,not a reset:continuations use original time.
    if maximum<120:raise TimeoutError('Original session window expired')
    unit='cinenest-retained-qualification-'+nonce
    command=['systemd-run','--user','--wait','--pipe','--collect','--quiet','--unit='+unit,'-p','CPUQuota=100%','-p','MemoryMax=1G','-p','MemorySwapMax=0','-p','TasksMax=128','-p','RuntimeMaxSec='+str(maximum),'-p','TimeoutStopSec=30','-p','NoNewPrivileges=yes','/usr/bin/python3',str(ROOT/'scripts/verify-cloud-connectivity.py'),'--retained-local-worker','--evidence',str(E)]
    try:
        result=subprocess.run(command,capture_output=True,text=True,timeout=min(1740,remaining-60))
        (E/('local-worker-output-'+str(state.get('attempts',0)+1)+'.log')).write_text(result.stdout+result.stderr)
    finally:retained_parent_cleanup(E)
    if result.returncode:raise RuntimeError('Bounded local worker failed;original window unchanged')
    return json.loads((E/'local-result.json').read_text())

_PREDECESSOR_RETAINED_MAIN=main

def main(argv=None):
    arguments=sys.argv[1:] if argv is None else argv
    if '--retained-local-worker' in arguments or '--qualify-retained-history' in arguments or '--resume-retained-history' in arguments:
        parser=argparse.ArgumentParser();parser.add_argument('--retained-local-worker',action='store_true');parser.add_argument('--qualify-retained-history',action='store_true');parser.add_argument('--resume-retained-history',action='store_true');parser.add_argument('--evidence',required=True);parser.add_argument('--execute',action='store_true');args=parser.parse_args(arguments)
        if args.retained_local_worker:
            import fcntl
            lease=(Path(args.evidence)/'local-worker.lock').open('a');fcntl.flock(lease,fcntl.LOCK_EX|fcntl.LOCK_NB)
            result=retained_local_worker(args.evidence);(Path(args.evidence)/'local-result.json').write_text(json.dumps(result,indent=2)+'\n');return 0 if result['status']=='PASS' and result['inventoryPreserved'] and result['withinApprovedWindow'] else 1
        if not args.execute:print('NOT_RUN: exact local approval and --execute required');return 0
        result=retained_local_launch(args.evidence,resume=args.resume_retained_history);print(json.dumps(result,indent=2));return 0 if result['status']=='PASS' else 1
    return _PREDECESSOR_RETAINED_MAIN(argv)


_PREDECESSOR_RETAINED_PREPARE_CONTEXT=prepare_context

def prepare_context(destination,sql_root,dependency_root):
    dest=Path(destination);sql=Path(sql_root);dep=Path(dependency_root)
    if not re.fullmatch('[a-f0-9]{64}',RETAINED_SCHEMA_SHA256):raise ValueError('Trusted disposable schema expectation not qualified')
    if not dest.is_absolute() or dest.exists() or not sql.is_absolute() or not dep.is_absolute():raise ValueError('Exclusive absolute context paths required')
    require_sql00_actor_manifest({name:fingerprint(sql/name) for name in SQL00_ACTOR_FILES});migrations=migration_inventory()
    for name,digest in DEPENDENCIES.items():
        if fingerprint(dep/name)!=digest:raise ValueError('Pinned dependency mismatch')
    dest.mkdir(mode=0o700)
    for directory in ['lib','migrations','sql']:(dest/directory).mkdir()
    try:
        for name in DEPENDENCIES:shutil.copyfile(dep/name,dest/'lib'/Path(name).name)
        for name in migrations:shutil.copyfile(ROOT/'backend/src/main/resources/db/migration'/name,dest/'migrations'/name)
        name='02-post-migration-runtime-grants.sql.proposed';shutil.copyfile(sql/name,dest/'sql'/name)
        (dest/'CinenestConnectivityProbe.java').write_text(JAVA_SOURCE)
        (dest/'Dockerfile').write_text(f'FROM {JDK} AS build\nWORKDIR /build\nCOPY lib/ lib/\nCOPY CinenestConnectivityProbe.java .\nRUN mkdir classes && javac -proc:none --release 25 -J-Xmx256m -J-XX:ActiveProcessorCount=1 -cp "/build/lib/*" -d classes CinenestConnectivityProbe.java\nFROM {JRE}\nWORKDIR /app\nCOPY --from=build --chown=10001:10001 /build/classes/ /app/classes/\nCOPY --chown=10001:10001 lib/ /app/lib/\nCOPY --chown=10001:10001 migrations/ /app/migrations/\nCOPY --chown=10001:10001 sql/ /app/sql/\nUSER 10001:10001\nENTRYPOINT ["java","-Xmx256m","-cp","/app/classes:/app/lib/*","CinenestConnectivityProbe"]\nCMD ["PROBE"]\n')
        manifest={str(p.relative_to(dest)):fingerprint(p) for p in dest.rglob('*') if p.is_file()};(dest/'context-sha256.json').write_text(json.dumps(manifest,indent=2)+'\n')
        return {'status':'PREPARED_OFFLINE_ONLY','files':manifest,'schemaSha256':RETAINED_SCHEMA_SHA256,'cloudRunConnectivity':'NOT_RUN','productionAuthorization':'NONE'}
    except BaseException:shutil.rmtree(dest);raise


def retained_verify_oci_base(archive,base,manifest):
    """Verify pinned OCI index -> platform manifest -> config;IDs are distinct."""
    def blob(digest):
        if not re.fullmatch('sha256:[a-f0-9]{64}',digest):raise ValueError('Invalid OCI digest')
        data=archive.extractfile('blobs/sha256/'+digest.removeprefix('sha256:')).read()
        if 'sha256:'+hashlib.sha256(data).hexdigest()!=digest:raise ValueError('Pinned OCI blob mismatch')
        return json.loads(data)
    if base['Id']!='sha256:b7528962e65d43064d8a8b67ba175367896bc948363ea2d3fcd7f10d34e1f048':raise ValueError('Unapproved base')
    index=blob(base['Id']);selected=[entry for entry in index['manifests'] if entry.get('platform',{}).get('architecture')==base['Architecture'] and entry.get('platform',{}).get('os')==base['Os']]
    if len(selected)!=1:raise ValueError('Pinned OCI platform ambiguous')
    platform=blob(selected[0]['digest']);digest=platform['config']['digest'];config=blob(digest)
    if 'sha256:'+hashlib.sha256(archive.extractfile(manifest['Config']).read()).hexdigest()!=digest or config['rootfs']['diff_ids']!=base['RootFS']['Layers']:raise ValueError('Pinned base config mismatch')
    return digest


def retained_local_seconds(evidence):
    e=Path(evidence);request=e/'local-completion-authorization.requested.json'
    if not request.exists():return 1800
    if fingerprint(request)!='5e230cacb99d3ec6be0b50087e8edcbdae5ecf56d000b8c37bad0188cbdfc24f':raise ValueError('Local completion exact request mismatch')
    receipt=json.loads((e/'local-completion-owner-approval.json').read_text())
    unit=json.loads((ROOT/'knowledge/execution/change-sets.json').read_text())['cloudRunRetainedFlywayHistory']
    if receipt!=unit.get('localCompletionAuthority') or receipt.get('source')!='Owner answer call_fccb96332e154e52b0448f0254b7b1ad: Approve exact local completion session' or receipt.get('maximumSecondsIncludingCleanup')!=900 or receipt.get('sessions')!=1 or receipt.get('cloudSessions')!=0:raise ValueError('Local completion authority unavailable')
    previous=json.loads((e/'consumed-local-session.original-window.json').read_text())
    if not previous.get('completed') or previous.get('inventoryPreserved') is not True:raise ValueError('Original session cleanup unverified')
    return 900

def retained_elapsed_within_budget(elapsed,maximum):
    return maximum in (900,1800) and isinstance(elapsed,(int,float)) and 0<=elapsed<=maximum

if __name__=='__main__':raise SystemExit(main())
