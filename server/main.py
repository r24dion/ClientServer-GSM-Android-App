from flask import Flask, request, jsonify
import os
import subprocess
import json


app = Flask(__name__)

sessions = {}


def a5_decrypt(cipher_hex: str, kc_hex: str, frame_hex: str) -> str:
    try:
        result = subprocess.run(
            ["java", "-cp", f".;{os.path.abspath('a5-kotlin.jar')}", "A5Decrypt", cipher_hex, kc_hex, frame_hex],
            capture_output=True,
            text=True,
            timeout=10
        )
        if result.returncode != 0:
            print(f"Java stderr: {result.stderr}")
            return f"JAVA ERROR: {result.stderr}"
        output = result.stdout.strip()
        return output if output else "EMPTY RESULT"
    except subprocess.TimeoutExpired:
        return "ERROR: Java timeout"
    except Exception as e:
        return f"ERROR: {e}"


def _comp128_compression(x):
    for i in range(0, 32, 2):
        a = x[i]
        b = x[i + 1]

        x[i] = ((a + 2 * b) % 256) & 0xFF
        x[i + 1] = ((2 * a + b) % 256) & 0xFF


def comp128_v1(ki: bytes, rand: bytes) -> bytes:
    ki_hex = ki.hex()
    rand_hex = rand.hex()

    result = subprocess.check_output(
        ["java", "Comp128Runner", ki_hex, rand_hex]
    ).decode().strip()

    return bytes.fromhex(result)


def _compression(x):
    for i in range(len(x)):
        x[i] = (x[i] ^ (i * 17)) & 0xFF


def _bits_from_bytes(x, bits):
    for i in range(128):
        bits[i] = (x[i // 4] >> (3 - (i % 4))) & 1


def _permutation(x, bits):
    for i in range(16, 32):
        x[i] = 0
    for i in range(128):
        x[(i // 8) + 16] |= bits[(i * 17) % 128] << (7 - (i % 8))


def generate_rand():
    return os.urandom(16)


def decrypt_xor(data: bytes, key: bytes) -> bytes:
    return bytes(b ^ key[i % len(key)] for i, b in enumerate(data))


def load_subscribers():
    key = os.environ.get('SUBSCRIBER_KEY', '').encode()
    if not key:
        raise RuntimeError("SUBSCRIBER_KEY not set")
    with open('subscribers.enc', 'rb') as f:
        enc_data = f.read()
    dec_data = decrypt_xor(enc_data, key)
    data = json.loads(dec_data)
    return {imsi: bytes.fromhex(ki_hex) for imsi, ki_hex in data.items()}


@app.route("/auth/request", methods=["POST"])
def request_rand():
    data = request.json
    imsi = data.get("imsi")
    subscribers = load_subscribers()

    if imsi not in subscribers:
        return jsonify({"error": "Unknown IMSI"}), 400

    ki = subscribers[imsi]
    rand = generate_rand()

    print("SERVER RAND:", rand.hex())

    result = comp128_v1(ki, rand)

    xres = result[:4]
    kc = result[4:12]

    print(f'XRES: {xres}')

    sessions[imsi] = {
        "rand": rand,
        "xres": xres,
        "kc": kc
    }

    return jsonify({
        "rand": rand.hex()
    })


@app.route("/auth/verify", methods=["POST"])
def verify():
    data = request.json
    imsi = data.get("imsi")
    sres_client = bytes.fromhex(data.get("sres"))

    session = sessions.get(imsi)

    if not session:
        return jsonify({"status": "FAIL", "reason": "No session"}), 400

    if sres_client == session["xres"]:
        return jsonify({
            "status": "OK",
            "kc": session["kc"].hex()
        })
    else:
        return jsonify({"status": "FAIL"})


@app.route("/send", methods=["POST"])
def receive_message():
    data = request.json

    imsi = data.get("imsi")
    cipher = data.get("data")
    frame = data.get("frame")

    session = sessions.get(imsi)

    if not session:
        return jsonify({"error": "No session"}), 400

    kc = session["kc"].hex()

    print("INPUT TO DECRYPT:")
    print("\tcipher:", cipher)
    print("\tkc:", kc)
    print("\tframe:", frame)

    try:
        plaintext = a5_decrypt(cipher, kc, frame)
    except Exception as e:
        return jsonify({"error": str(e)}), 500

    print(f"[DECRYPTED MESSAGE] {plaintext}")

    return jsonify({
        "status": "RECEIVED",
        "plaintext": plaintext
    })


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=True)
