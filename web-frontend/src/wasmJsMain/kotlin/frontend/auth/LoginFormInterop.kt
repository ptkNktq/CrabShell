@file:OptIn(ExperimentalWasmJsInterop::class)

package frontend.auth

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsString

// パスワードマネージャ連携用の隠しフォーム操作

@JsFun("(v) => { document.getElementById('login-email').value = v; }")
external fun setLoginEmailValue(value: JsString)

@JsFun("(v) => { document.getElementById('login-password').value = v; }")
external fun setLoginPasswordValue(value: JsString)

@JsFun("() => document.getElementById('login-email').value")
external fun getLoginEmailValue(): JsString

@JsFun("() => document.getElementById('login-password').value")
external fun getLoginPasswordValue(): JsString

@JsFun("(show) => { document.getElementById('login-form').style.display = show ? '' : 'none'; }")
external fun setLoginFormVisible(show: Boolean)
