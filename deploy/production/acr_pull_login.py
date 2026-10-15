"""Log Docker in to the production ACR with a one-hour RAM token.

Install alibabacloud_cr20181201 from PyPI before running this script. The
RAM user's existing AccessKey must have cr:GetAuthorizationToken and pull
permission; no fixed Registry password is required.
"""

import argparse
from pathlib import Path
import subprocess

from alibabacloud_cr20181201.client import Client
from alibabacloud_cr20181201 import models
from alibabacloud_tea_openapi import models as openapi

INSTANCE_ID = "cri-s2fls578q1j9o8sl"
PRIVATE_REGISTRY = "lynxad-prod-acr-registry-vpc.cn-beijing.cr.aliyuncs.com"


def credentials(path):
    values = {}
    for line in Path(path).read_text(encoding="utf-8-sig").splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip().strip('"\'')
    return values["OSS_ACCESS_KEY_ID"], values["OSS_ACCESS_KEY_SECRET"]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("env_file", type=Path)
    parser.add_argument("--registry", default=PRIVATE_REGISTRY)
    args = parser.parse_args()
    access_key_id, access_key_secret = credentials(args.env_file)
    client = Client(openapi.Config(
        access_key_id=access_key_id,
        access_key_secret=access_key_secret,
        region_id="cn-beijing",
        endpoint="cr.cn-beijing.aliyuncs.com",
    ))
    result = client.get_authorization_token(models.GetAuthorizationTokenRequest(
        expires_in_hours=1, instance_id=INSTANCE_ID,
    )).body
    if not result.is_success or not result.authorization_token:
        raise RuntimeError("ACR temporary authorization failed")
    subprocess.run(
        ["docker", "login", args.registry, "--username", result.temp_username, "--password-stdin"],
        input=result.authorization_token + "\n", text=True, check=True,
    )
    print("ACR login succeeded; token expires in one hour")


if __name__ == "__main__":
    main()
