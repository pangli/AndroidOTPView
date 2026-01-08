package com.zorro.optview

interface OnOtpStateListener {
    fun onFocusChanged(hasFocus: Boolean)
    fun onTextChanged(text: CharSequence)
    fun onOtpCompleted(otp: String)
}