"""Generate a domain-scoped Nginx include from Cloudflare's official ranges.
Trust only Cloudflare addresses; never trust CF-Connecting-IP from arbitrary peers.
Run on VPS as administrator, then nginx -t and reload nginx.
"""
import ipaddress,urllib.request,pathlib,os
networks=[]
for version in (4,6):
 with urllib.request.urlopen(f'https://www.cloudflare.com/ips-v{version}',timeout=20) as response:
  lines=response.read().decode('ascii').splitlines()
 if len(lines)<5:raise RuntimeError('Cloudflare range list is unexpectedly short')
 for line in lines:
  network=ipaddress.ip_network(line.strip(),strict=True)
  if network.version!=version:raise RuntimeError('Unexpected IP version')
  networks.append(str(network))
target=pathlib.Path('/etc/nginx/snippets/xuannhashop-cloudflare-realip.conf')
target.parent.mkdir(parents=True,exist_ok=True)
temporary=target.with_suffix('.tmp')
temporary.write_text('# Official Cloudflare ranges; applies only where included.\n'+''.join(f'set_real_ip_from {network};\n' for network in networks)+'real_ip_header CF-Connecting-IP;\nreal_ip_recursive on;\n')
os.replace(temporary,target)
print(f'Generated trusted proxy include: {len(networks)} networks')
