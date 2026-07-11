#!/usr/bin/env python3
"""Quality gate: falla si algun reporte XML de OWASP ZAP tiene alertas de riesgo HIGH.

Uso: zap-report-gate.py <reporte1.xml> [reporte2.xml ...]

ZAP codifica el riesgo como <riskcode> (0=Informational, 1=Low, 2=Medium, 3=High) dentro
de cada <alertitem> del reporte XML estandar (zap-baseline.py/zap-api-scan.py -x <file>).
"""
import sys
import xml.etree.ElementTree as ET

HIGH_RISKCODE = "3"


def find_high_alerts(xml_path):
    tree = ET.parse(xml_path)
    root = tree.getroot()
    alerts = []
    for site in root.findall("site"):
        site_name = site.get("name", "?")
        for alertitem in site.findall("./alerts/alertitem"):
            riskcode = alertitem.findtext("riskcode", "")
            if riskcode == HIGH_RISKCODE:
                alerts.append(
                    {
                        "site": site_name,
                        "name": alertitem.findtext("name", "?"),
                        "riskdesc": alertitem.findtext("riskdesc", "?"),
                        "count": alertitem.findtext("count", "?"),
                        "cweid": alertitem.findtext("cweid", "?"),
                        "uris": [
                            instance.findtext("uri", "?")
                            for instance in alertitem.findall("./instances/instance")
                        ],
                    }
                )
    return alerts


def main(argv):
    if not argv:
        print("Uso: zap-report-gate.py <reporte1.xml> [reporte2.xml ...]", file=sys.stderr)
        return 2

    total_high = 0
    for path in argv:
        try:
            alerts = find_high_alerts(path)
        except (ET.ParseError, FileNotFoundError) as exc:
            print(f"ERROR leyendo {path}: {exc}", file=sys.stderr)
            return 2

        print(f"\n=== {path}: {len(alerts)} alerta(s) HIGH ===")
        for alert in alerts:
            print(f"  [HIGH] {alert['name']} (CWE-{alert['cweid']}, {alert['riskdesc']}, x{alert['count']})")
            for uri in alert["uris"][:5]:
                print(f"    - {uri}")
            if len(alert["uris"]) > 5:
                print(f"    ... y {len(alert['uris']) - 5} más")
        total_high += len(alerts)

    print(f"\nTotal de alertas HIGH en todos los reportes: {total_high}")
    if total_high > 0:
        print("Quality gate FALLIDO: se requieren 0 vulnerabilidades HIGH.", file=sys.stderr)
        return 1

    print("Quality gate OK: 0 vulnerabilidades HIGH.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
