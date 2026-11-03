"""Uploads the signed artifacts of a release to Maven Central as one deployment and waits for the outcome.

Gradle writes them to a local Maven repository first (publishAllPublicationsToCentralBundleRepository); this script
zips that repository and sends it to the Central Publisher API. Credentials come from the environment variables
MAVEN_CENTRAL_USERNAME and MAVEN_CENTRAL_PASSWORD (a user token from central.sonatype.com, not the login).

Usage: python scripts/publish_to_central.py BUNDLE_DIRECTORY DEPLOYMENT_NAME [--automatic]

Without --automatic the deployment stops after validation, and someone has to press "Publish" on
https://central.sonatype.com/publishing/deployments; releases on Central can never be changed or deleted.
"""

import base64
import io
import json
import os
import sys
import time
import urllib.parse
import urllib.request
import uuid
import zipfile
from pathlib import Path

PUBLISHER_API = "https://central.sonatype.com/api/v1/publisher"
STATUS_POLL_SECONDS = 10
STATUS_POLL_LIMIT = 90
FINAL_STATES_BY_MODE = {
    "AUTOMATIC": {"PUBLISHED"},
    "USER_MANAGED": {"VALIDATED", "PUBLISHED"},
}

type DeploymentId = str
type DeploymentState = str


def bundle_zip(repository: Path) -> bytes:
    """Gradle's maven-metadata files describe a local repository and are not part of a Central bundle."""
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", zipfile.ZIP_DEFLATED) as bundle:
        for file in sorted(repository.rglob("*")):
            is_artifact_file = file.is_file() and not file.name.startswith("maven-metadata")
            if is_artifact_file:
                bundle.write(file, file.relative_to(repository).as_posix())
    return buffer.getvalue()


def authorization_header() -> str:
    token = f"{os.environ['MAVEN_CENTRAL_USERNAME']}:{os.environ['MAVEN_CENTRAL_PASSWORD']}"
    return "Bearer " + base64.b64encode(token.encode()).decode()


def multipart_body(bundle: bytes, boundary: str) -> bytes:
    header = (
        f"--{boundary}\r\n"
        'Content-Disposition: form-data; name="bundle"; filename="bundle.zip"\r\n'
        "Content-Type: application/octet-stream\r\n\r\n"
    ).encode()
    return header + bundle + f"\r\n--{boundary}--\r\n".encode()


def upload(bundle: bytes, deployment_name: str, publishing_type: str) -> DeploymentId:
    boundary = uuid.uuid4().hex
    url = f"{PUBLISHER_API}/upload?name={urllib.parse.quote(deployment_name)}&publishingType={publishing_type}"
    request = urllib.request.Request(url, data=multipart_body(bundle, boundary), method="POST")
    request.add_header("Authorization", authorization_header())
    request.add_header("Content-Type", f"multipart/form-data; boundary={boundary}")
    with urllib.request.urlopen(request) as response:
        return response.read().decode().strip()


def deployment_status(deployment_id: DeploymentId) -> dict[str, object]:
    request = urllib.request.Request(f"{PUBLISHER_API}/status?id={deployment_id}", method="POST")
    request.add_header("Authorization", authorization_header())
    with urllib.request.urlopen(request) as response:
        status: dict[str, object] = json.loads(response.read())
        return status


def await_final_state(deployment_id: DeploymentId, publishing_type: str) -> DeploymentState:
    for _ in range(STATUS_POLL_LIMIT):
        status = deployment_status(deployment_id)
        state = str(status["deploymentState"])
        print(f"deployment {deployment_id}: {state}")
        if state == "FAILED":
            sys.exit(f"Central rejected the deployment: {json.dumps(status.get('errors'), indent=2)}")
        if state in FINAL_STATES_BY_MODE[publishing_type]:
            return state
        time.sleep(STATUS_POLL_SECONDS)
    sys.exit(f"deployment {deployment_id} did not finish in time; see https://central.sonatype.com/publishing/deployments")


def main() -> None:
    repository, deployment_name = Path(sys.argv[1]), sys.argv[2]
    publishing_type = "AUTOMATIC" if "--automatic" in sys.argv[3:] else "USER_MANAGED"
    bundle = bundle_zip(repository)
    print(f"uploading {len(bundle) // 1024 // 1024} MB as {publishing_type}")
    deployment_id = upload(bundle, deployment_name, publishing_type)
    state = await_final_state(deployment_id, publishing_type)
    if state == "VALIDATED":
        print("validated; publish it on https://central.sonatype.com/publishing/deployments")


if __name__ == "__main__":
    main()
