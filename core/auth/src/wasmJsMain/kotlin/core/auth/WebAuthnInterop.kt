@file:OptIn(ExperimentalWasmJsInterop::class)

package core.auth

import kotlin.js.Promise

// WebAuthn API がブラウザでサポートされているか確認
@JsFun("() => typeof PublicKeyCredential !== 'undefined'")
external fun isWebAuthnAvailable(): Boolean

// navigator.credentials.create() を呼び出してパスキーを登録
// optionsJson は PublicKeyCredentialCreationOptions の JSON 文字列
// 結果は credential.response を toJSON() した JSON 文字列
@JsFun(
    """(optionsJson) => {
    const options = JSON.parse(optionsJson);

    // Base64URL → ArrayBuffer 変換
    function base64urlToBuffer(base64url) {
        const padding = '='.repeat((4 - base64url.length % 4) % 4);
        const base64 = base64url.replace(/-/g, '+').replace(/_/g, '/') + padding;
        const binary = atob(base64);
        const bytes = new Uint8Array(binary.length);
        for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
        return bytes.buffer;
    }

    // ArrayBuffer → Base64URL 変換
    function bufferToBase64url(buffer) {
        const bytes = new Uint8Array(buffer);
        let binary = '';
        for (let i = 0; i < bytes.length; i++) binary += String.fromCharCode(bytes[i]);
        return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
    }

    options.challenge = base64urlToBuffer(options.challenge);
    options.user.id = base64urlToBuffer(options.user.id);
    if (options.excludeCredentials) {
        options.excludeCredentials = options.excludeCredentials.map(c => ({
            ...c,
            id: base64urlToBuffer(c.id)
        }));
    }

    return navigator.credentials.create({ publicKey: options }).then(credential => {
        const response = credential.response;
        const result = {
            id: credential.id,
            rawId: bufferToBase64url(credential.rawId),
            type: credential.type,
            response: {
                clientDataJSON: bufferToBase64url(response.clientDataJSON),
                attestationObject: bufferToBase64url(response.attestationObject),
                transports: response.getTransports ? response.getTransports() : []
            }
        };
        return JSON.stringify(result);
    });
}""",
)
external fun webAuthnCreateCredential(optionsJson: JsString): Promise<JsString>

// navigator.credentials.get() を呼び出してパスキーで認証
// optionsJson は PublicKeyCredentialRequestOptions の JSON 文字列
// 結果は credential.response を toJSON() した JSON 文字列
@JsFun(
    """(optionsJson) => {
    const options = JSON.parse(optionsJson);

    function base64urlToBuffer(base64url) {
        const padding = '='.repeat((4 - base64url.length % 4) % 4);
        const base64 = base64url.replace(/-/g, '+').replace(/_/g, '/') + padding;
        const binary = atob(base64);
        const bytes = new Uint8Array(binary.length);
        for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
        return bytes.buffer;
    }

    function bufferToBase64url(buffer) {
        const bytes = new Uint8Array(buffer);
        let binary = '';
        for (let i = 0; i < bytes.length; i++) binary += String.fromCharCode(bytes[i]);
        return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
    }

    options.challenge = base64urlToBuffer(options.challenge);
    if (options.allowCredentials) {
        options.allowCredentials = options.allowCredentials.map(c => ({
            ...c,
            id: base64urlToBuffer(c.id)
        }));
    }

    return navigator.credentials.get({ publicKey: options }).then(credential => {
        const response = credential.response;
        const result = {
            id: credential.id,
            rawId: bufferToBase64url(credential.rawId),
            type: credential.type,
            response: {
                clientDataJSON: bufferToBase64url(response.clientDataJSON),
                authenticatorData: bufferToBase64url(response.authenticatorData),
                signature: bufferToBase64url(response.signature),
                userHandle: response.userHandle ? bufferToBase64url(response.userHandle) : null
            }
        };
        return JSON.stringify(result);
    });
}""",
)
external fun webAuthnGetCredential(optionsJson: JsString): Promise<JsString>
