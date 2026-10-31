"""Zeigt die Fehlermeldungen fehlgeschlagener Tests aus build/test-results (optional gefiltert nach Klassenname)."""
import glob
import sys
import xml.etree.ElementTree as ET

MAX_MESSAGE_LENGTH = 600
class_filter = sys.argv[1] if len(sys.argv) > 1 else ""

for result_file in glob.glob("build/test-results/test/*.xml"):
    if class_filter not in result_file:
        continue
    for test_case in ET.parse(result_file).getroot().iter("testcase"):
        for failure in test_case.findall("failure"):
            print(test_case.get("name"), "::", (failure.get("message") or "")[:MAX_MESSAGE_LENGTH])
