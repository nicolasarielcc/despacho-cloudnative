import json
import os
import time
import urllib.request
import ssl
import jwt

TENANT_DOMAIN = "despachoservice2.onmicrosoft.com"
TENANT_ID = "5199d2b5-40ed-44c1-a8e5-f4a83132a743"
POLICY = "B2C_1_despacho_signin"
CLIENT_ID = "0a27f262-a186-457f-b7b5-eb43b284cd4c"
ISSUER = f"https://{TENANT_DOMAIN.split('.')[0]}.b2clogin.com/{TENANT_ID}/v2.0/"
JWKS_URI = f"https://{TENANT_DOMAIN.split('.')[0]}.b2clogin.com/{TENANT_DOMAIN}/discovery/v2.0/keys?p={POLICY}"

_cache = {"keys": None, "ts": 0}


def _fetch_jwks():
    now = time.time()
    if _cache["keys"] and now - _cache["ts"] < 3600:
        return _cache["keys"]

    ctx = ssl.create_default_context()
    req = urllib.request.Request(JWKS_URI, headers={"User-Agent": "lambda-b2c-authorizer"})
    with urllib.request.urlopen(req, timeout=10, context=ctx) as r:
        _cache["keys"] = json.loads(r.read())
        _cache["ts"] = now
    return _cache["keys"]


def handler(event, context):
    auth = event.get("headers", {}).get("authorization", "")
    if auth.lower().startswith("bearer "):
        auth = auth[7:]

    if not auth:
        print("[AUTHORIZER] Sin token")
        return {"isAuthorized": False}

    try:
        jwks = _fetch_jwks()
        unverified_header = jwt.get_unverified_header(auth)
        kid = unverified_header.get("kid")

        public_key = None
        for jwk in jwks.get("keys", []):
            if jwk.get("kid") == kid:
                public_key = jwt.algorithms.RSAAlgorithm.from_jwk(json.dumps(jwk))
                break

        if not public_key:
            print(f"[AUTHORIZER] JWK no encontrado para kid={kid}")
            return {"isAuthorized": False}

        payload = jwt.decode(
            auth,
            public_key,
            algorithms=["RS256"],
            audience=CLIENT_ID,
            issuer=ISSUER,
            options={"verify_exp": True, "verify_nbf": True},
        )

        rol = payload.get("extension_consultaRole", "consulta")
        print(f"[AUTHORIZER] OK sub={payload.get('sub')} rol={rol}")

        return {
            "isAuthorized": True,
            "context": {
                "rol": rol,
                "sub": payload.get("sub", ""),
            },
        }

    except jwt.ExpiredSignatureError:
        print("[AUTHORIZER] Token expirado")
        return {"isAuthorized": False}
    except jwt.InvalidAudienceError:
        print("[AUTHORIZER] Audiencia invalida")
        return {"isAuthorized": False}
    except jwt.InvalidIssuerError:
        print("[AUTHORIZER] Issuer invalido")
        return {"isAuthorized": False}
    except Exception as e:
        print(f"[AUTHORIZER] Error: {e}")
        return {"isAuthorized": False}
