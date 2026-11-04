"""Create an isolated FirstStep MySQL instance; never alter the machine's MySQL80 service."""
from pathlib import Path
import json
import secrets
import socket
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
LOCAL = ROOT / '.local' / 'mysql'
DATA = LOCAL / 'data'
BASE = Path(r'C:\Program Files\MySQL\MySQL Server 8.0')
MYSQLD = BASE / 'bin' / 'mysqld.exe'
PORT = 3307

def listening():
    try:
        with socket.create_connection(('127.0.0.1', PORT), timeout=1):
            return True
    except OSError:
        return False

def main():
    if not MYSQLD.is_file():
        raise SystemExit('MySQL Server 8.0 installation was not found.')
    LOCAL.mkdir(parents=True, exist_ok=True)
    credentials = LOCAL / 'admin.json'
    env = ROOT / 'backend' / '.env'
    first_run = not credentials.exists()
    if first_run:
        if listening() or DATA.exists():
            raise SystemExit('3307 or data directory is already in use. No existing data was changed.')
        settings = {'root_password': secrets.token_urlsafe(32), 'api_password': secrets.token_urlsafe(32)}
        credentials.write_text(json.dumps(settings), encoding='utf-8')
        env.parent.mkdir(exist_ok=True)
        if env.exists():
            raise SystemExit('backend/.env already exists; refusing to overwrite it.')
        env.write_text('DATABASE_URL=mysql+pymysql://firststep_api:' + settings['api_password']
            + '@127.0.0.1:3307/firststep?charset=utf8mb4\nDEVELOPMENT_LOGIN_ENABLED=true\nGOOGLE_WEB_CLIENT_ID=\n', encoding='utf-8')
    else:
        settings = json.loads(credentials.read_text(encoding='utf-8'))
    if listening():
        print('FirstStep MySQL endpoint is already listening on 127.0.0.1:3307.')
        return
    if not (DATA / 'mysql').is_dir():
        result = subprocess.run([str(MYSQLD), '--no-defaults', '--initialize-insecure',
            '--basedir=' + str(BASE), '--datadir=' + str(DATA)], capture_output=True, text=True)
        if result.returncode:
            (LOCAL / 'initialization-error.log').write_text(result.stderr, encoding='utf-8')
            raise SystemExit('MySQL initialization failed; see .local/mysql/initialization-error.log.')
    config = LOCAL / 'my.ini'
    config.write_text('[mysqld]\nbasedir="' + BASE.as_posix() + '"\ndatadir="' + DATA.as_posix()
        + '"\nport=3307\nbind-address=127.0.0.1\nmysqlx=OFF\nskip-name-resolve=ON\n'
        + 'innodb-buffer-pool-size=64M\nperformance-schema=OFF\ncharacter-set-server=utf8mb4\n'
        + 'collation-server=utf8mb4_unicode_ci\nlog-error="' + (LOCAL / 'server.log').as_posix()
        + '"\npid-file="' + (LOCAL / 'mysql.pid').as_posix() + '"\n', encoding='utf-8')
    init = LOCAL / 'initialize-user.sql'
    # The initialization file runs before accepting clients. Passwords stay in ignored local files.
    init.write_text("ALTER USER 'root'@'localhost' IDENTIFIED BY '" + settings['root_password'] + "';\n"
        + 'CREATE DATABASE IF NOT EXISTS firststep CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;\n'
        + "CREATE USER IF NOT EXISTS 'firststep_api'@'127.0.0.1' IDENTIFIED BY '" + settings['api_password'] + "';\n"
        + "GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES ON firststep.* TO 'firststep_api'@'127.0.0.1';\n", encoding='utf-8')
    process = subprocess.Popen([str(MYSQLD), '--defaults-file=' + str(config), '--init-file=' + str(init)],
        stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        creationflags=subprocess.CREATE_NO_WINDOW)
    for _ in range(60):
        if process.poll() is not None:
            raise SystemExit('FirstStep MySQL stopped during startup; see .local/mysql/server.log.')
        if listening():
            init.unlink(missing_ok=True)
            print('FirstStep MySQL started: 127.0.0.1:3307, database firststep, user firststep_api.')
            return
        time.sleep(1)
    raise SystemExit('MySQL startup timed out; see .local/mysql/server.log.')

if __name__ == '__main__':
    main()
