# Deployment Notes

## Current Live Topology

The live application is deployed on `jia.zhuzhux.com`. Do not deploy backend JARs, frontend bundles, or Docker Compose app changes to `chenqi.tminos.com`.

Public browser access:

```bash
http://jia.zhuzhux.com:18080/
http://chenqi.tminos.com:20080/
```

`chenqi.tminos.com:20080` is only a compatibility entrance that proxies traffic to `jia.zhuzhux.com:18080`. Treat chenqi as an edge/proxy host only unless the task is explicitly about chenqi nginx forwarding.

## SSH

Primary deploy host:

```bash
ssh jia.zhuzhux.com
```

Legacy proxy host, do not deploy app packages here:

```bash
ssh -p 20022 -i ~/.ssh/id_ed25519_hanfeihu_114_227_94_234_20022 -o IdentitiesOnly=yes hanfeihu@chenqi.tminos.com
```

## Runtime Layout On Jia

- Backend compose directory: `/home/temu-upin-backend`
- Backend container: `temu-upin-backend-jia`
- Backend port mapping: `127.0.0.1:18088 -> 8082`
- Frontend nginx root: `/home/temu-upin-frontend/html-root/root`
- Legacy `/temu/` frontend root: `/home/temu-upin-frontend/html-root/temu`
- Public nginx entry: `http://jia.zhuzhux.com:18080/`
- API entry: `http://jia.zhuzhux.com:18080/api/`
- Database: PostgreSQL container `postgres`, database `temu_upin`

## Backend Publish

Build locally, upload only to jia, rebuild the jia container:

```bash
mvn -pl temu-upin-backend -am clean package -DskipTests
scp temu-upin-backend/target/product-scene-1.0.0.jar jia.zhuzhux.com:/tmp/product-scene-1.0.0.jar
ssh jia.zhuzhux.com
sudo cp /tmp/product-scene-1.0.0.jar /home/temu-upin-backend/product-scene-1.0.0.jar
cd /home/temu-upin-backend
docker compose build temu-upin-backend
docker compose up -d temu-upin-backend
```

Verify both public entries:

```bash
curl -sS -i http://jia.zhuzhux.com:18080/api/auth/me
curl -sS -i http://chenqi.tminos.com:20080/api/auth/me
```

The unauthenticated API check should return `401` with `未登录或登录已过期`.

## Frontend Publish

Build locally, upload the bundle to jia, and extract into the jia nginx root:

```bash
cd react-ant-6.3.5-admin
npm run build
COPYFILE_DISABLE=1 tar -C dist -czf /tmp/temu-upin-frontend-dist.tar.gz .
scp /tmp/temu-upin-frontend-dist.tar.gz jia.zhuzhux.com:/tmp/temu-upin-frontend-dist.tar.gz
ssh jia.zhuzhux.com
ts=$(date +%Y%m%d-%H%M%S)
sudo cp -a /home/temu-upin-frontend/html-root/root /home/temu-upin-frontend/html-root/root.bak-$ts
sudo find /home/temu-upin-frontend/html-root/root -mindepth 1 -maxdepth 1 -exec rm -rf {} +
sudo tar -xzf /tmp/temu-upin-frontend-dist.tar.gz -C /home/temu-upin-frontend/html-root/root
sudo nginx -s reload
```

Verify:

```bash
curl -I http://jia.zhuzhux.com:18080/
curl -I http://chenqi.tminos.com:20080/
curl -I http://jia.zhuzhux.com:18080/platform/product-dashboard
curl -I http://chenqi.tminos.com:20080/platform/product-dashboard
```

## Important Caveats

- Do not use the old chenqi deployment commands for application releases.
- If frontend root and `/temu/` compatibility builds are both needed, build and deploy them sequentially. Do not run parallel builds that share the same `dist` directory.
- If backend startup fails with PostgreSQL connection exhaustion, inspect `pg_stat_activity` on the jia `postgres` container before restarting repeatedly.

## Local Backend Database Tunnel

Local profile uses PostgreSQL through `127.0.0.1:15432`. Point the tunnel to jia:

```bash
ssh jia.zhuzhux.com -N -L 127.0.0.1:15432:127.0.0.1:5432
```
