package com.feng.system.module.image;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;

public final class SafeUpstreamUrl {
    private SafeUpstreamUrl() {}

    public static String requirePublicHttps(String value) {
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null)
                throw new IllegalArgumentException();
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

    private static boolean uniqueLocal(InetAddress address) {
        return address instanceof Inet6Address && (address.getAddress()[0] & 0xfe) == 0xfc;
    }
}
