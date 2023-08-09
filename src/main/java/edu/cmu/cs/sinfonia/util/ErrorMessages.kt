/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package edu.cmu.cs.sinfonia.util

import android.os.RemoteException
import edu.cmu.cs.sinfonia.R
import edu.cmu.cs.sinfonia.SinfoniaService
import edu.cmu.cs.sinfonia.model.SinfoniaTier3.DeployException
import edu.cmu.cs.sinfonia.wireguard.WireGuardClient.WireGuardException

object ErrorMessages {
    private val DE_REASON_MAP = mapOf(
        DeployException.Reason.UNKNOWN to R.string.deploy_error_reason_unknown,
        DeployException.Reason.UNAVAILABLE to R.string.deploy_error_reason_unavailable,
        DeployException.Reason.URL_NOT_FOUND to R.string.deploy_error_reason_url_not_found,
        DeployException.Reason.INVALID_UUID to R.string.deploy_error_reason_invalid_uuid,
        DeployException.Reason.UUID_NOT_FOUND to R.string.deploy_error_reason_uuid_not_found,
        DeployException.Reason.CANNOT_CAST_RESPONSE to R.string.deploy_error_reason_cannot_cast_response,
        DeployException.Reason.DEPLOYMENT_NOT_FOUND to R.string.deploy_error_reason_deployment_not_found
    )
    private val TE_REASON_MAP = mapOf(
        TunnelException.Reason.UNKNOWN to R.string.tunnel_error_reason_unknown,
        TunnelException.Reason.ALREADY_EXIST to R.string.tunnel_error_reason_already_exist,
        TunnelException.Reason.INVALID_NAME to R.string.tunnel_error_reason_invalid_name,
        TunnelException.Reason.NOT_FOUND to R.string.tunnel_error_reason_not_found,
        TunnelException.Reason.ALREADY_UP to R.string.tunnel_error_reason_already_up,
        TunnelException.Reason.ALREADY_DOWN to R.string.tunnel_error_reason_already_down,
        TunnelException.Reason.ALREADY_TOGGLE to R.string.tunnel_error_reason_already_toggle,
        TunnelException.Reason.UNAUTHORIZED_ACCESS to R.string.tunnel_error_reason_unauthorized_access
    )
    private val WGE_REASON_MAP = mapOf(
        WireGuardException.Reason.UNKNOWN to R.string.wireguard_error_reason_unknown,
        WireGuardException.Reason.DISCONNECTED to R.string.wireguard_error_reason_service_not_connected,
        WireGuardException.Reason.PERMISSION_DENIED to R.string.wireguard_error_reason_permission_denied
    )

    operator fun get(throwable: Throwable?): String {
        val resources = SinfoniaService.get().resources
        if (throwable == null) return resources.getString(R.string.unknown_error)
        val rootCause = rootCause(throwable)
        return when {
            rootCause is DeployException -> {
                resources.getString(DE_REASON_MAP.getValue(rootCause.getReason()), *rootCause.getFormat())
            }
            rootCause is TunnelException -> {
                resources.getString(TE_REASON_MAP.getValue(rootCause.getReason()), *rootCause.getFormat())
            }
            rootCause is WireGuardException -> {
                resources.getString(WGE_REASON_MAP.getValue(rootCause.getReason()), *rootCause.getFormat())
            }
            rootCause.localizedMessage != null -> {
                rootCause.localizedMessage!!
            }

            else -> {
                val errorType = rootCause.javaClass.simpleName
                resources.getString(R.string.generic_error, errorType)
            }
        }
    }

    private fun rootCause(throwable: Throwable): Throwable {
        var cause = throwable
        while (cause.cause != null) {
            val nextCause = cause.cause!!
            if (nextCause is RemoteException) break
            cause = nextCause
        }
        return cause
    }
}