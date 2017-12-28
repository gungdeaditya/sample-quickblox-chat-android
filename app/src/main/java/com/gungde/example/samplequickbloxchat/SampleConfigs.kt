package com.gungde.example.samplequickbloxchat

/**
 * Created by gungdeaditya on 12/23/17.
 */

data class SampleConfigs(
		val users_tag: String, //webrtcusers
		val users_password: String, //x6Bt0VDy5
		val port: Int, //5223
		val socket_timeout: Int, //300
		val keep_alive: Boolean, //true
		val use_tls: Boolean, //true
		val auto_join: Boolean, //false
		val auto_mark_delivered: Boolean, //true
		val reconnection_allowed: Boolean, //true
		val allow_listen_network: Boolean //true
)