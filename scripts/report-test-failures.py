#!/usr/bin/env python3
"""Expose bounded test diagnostics in Actions annotations without credentials."""
from pathlib import Path
import xml.etree.ElementTree as ET

def escaped(value):
    return value.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A').replace(',', '%2C').replace(':', '%3A')

reported = 0
for path in sorted(Path('.').glob('**/target/surefire-reports/TEST-*.xml')):
    root = ET.parse(path).getroot()
    for case in root.findall('testcase'):
        failure = case.find('failure')
        if failure is None:
            failure = case.find('error')
        if failure is None:
            continue
        if reported < 15:
            title = case.get('classname', '') + '.' + case.get('name', '')
            lines = (failure.text or failure.get('message', '')).splitlines()
            # Keep the first cause and nested root causes; omit expected/actual payloads.
            useful = [line for line in lines if 'Caused by:' in line]
            message = failure.get('type', '') + '\n' + '\n'.join(lines[:2] + useful)
            print('::error title=' + escaped(title[:200]) + '::' + escaped(message[:5000]))
        reported += 1
print('Surefire failing cases:', reported)
