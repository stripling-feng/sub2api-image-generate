package com.feng.system.module.image.support;

import com.feng.system.module.image.exception.ImageApiException;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;

/**
 * 上游 URL 安全校验工具:确保用户提供的上游地址是公网 HTTPS 端点,防止 SSRF 攻击。
 */
public final class SafeUpstreamUrl {
    private SafeUpstreamUrl() {}

    /**
     * 校验并返回合法的公网 HTTPS 地址:要求协议为 https、无用户信息段,
     * 且域名解析出的所有 IP 均不属于内网/回环/链路本地/组播等私有地址段;不合法则抛出 422。
     *
     * @param value 待校验的 URL 字符串
     */
    public static String requirePublicHttps(String value) {
        try {
            URI uri = URI.create(value);
            // 禁止携带 userInfo(如 user:pass@host),避免混淆解析
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null)
                throw new IllegalArgumentException();
            // 解析全部 DNS 记录并逐一检查,防止通过多记录混入内网地址
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress() || uniqueLocal(address))
                    throw new IllegalArgumentException();
            }
            return uri.toString();
        } catch (Exception e) {
            throw new ImageApiException(422, "baseUrl must be a public HTTPS endpoint.");
        }
    }

    // 判断 IPv6 唯一本地地址(ULA,fc00::/7 段)
    private static boolean uniqueLocal(InetAddress address) {
        return address instanceof Inet6Address && (address.getAddress()[0] & 0xfe) == 0xfc;
    }
}
