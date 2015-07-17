/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.farmgeek4life.jenkins.negotiatesso;

import hudson.security.ACL;
import hudson.security.SecurityRealm;
import hudson.util.VersionNumber;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.security.SecurityListener;
import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.providers.UsernamePasswordAuthenticationToken;
import org.acegisecurity.userdetails.UserDetails;
import waffle.windows.auth.IWindowsIdentity;
import waffle.windows.auth.IWindowsSecurityContext;
import waffle.windows.auth.impl.WindowsAuthProviderImpl;

/**
 *
 * @author Bryson Gibbons
 */
public class WindowAuthForJenkins extends WindowsAuthProviderImpl {
    
    private static final Logger LOGGER = Logger.getLogger(NegotiateSSO.class.getName());
    
    /**
     * Called by BasicSecurityFilterProvider
     */
    @Override
    public IWindowsIdentity logonUser(final String username, final String password)
    {
       IWindowsIdentity id = super.logonUser(username, password);
       authenticateJenkins(id);
       return id;
    }
    
    /**
     * Called by NegotiateSecurityFilterProvider
     */
    @Override
    public IWindowsSecurityContext acceptSecurityToken(final String connectionId, final byte[] token, final String securityPackage) {
        IWindowsSecurityContext context = super.acceptSecurityToken(connectionId, token, securityPackage);
        authenticateJenkins(context.getIdentity());
        return context;
    }
    
    /**
     * Perform the authentication methods for Jenkins
     */
    private void authenticateJenkins(IWindowsIdentity windowsIdentity) {
        String principalName = windowsIdentity.getFqn();
        if (principalName.contains("@")) {
            principalName = principalName.substring(0, principalName.indexOf("@"));
        }
        if (principalName.contains("\\")) {
            principalName = principalName.substring(principalName.indexOf("\\") + 1);
        }
        SecurityRealm realm = Jenkins.getInstance().getSecurityRealm();
        UserDetails userDetails = realm.loadUserByUsername(principalName);
        Authentication authToken = new UsernamePasswordAuthenticationToken(
                        userDetails.getUsername(),
                        userDetails.getPassword(),
                        userDetails.getAuthorities());
        SecurityContext context = ACL.impersonate(authToken);
        if (Jenkins.getVersion().isNewerThan(new VersionNumber("1.568"))) {
            try {
                Method fireLoggedIn = SecurityListener.class.getMethod("fireLoggedIn", String.class);
                fireLoggedIn.invoke(null, userDetails.getUsername());
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to invoke fireLoggedIn method {0}", e);
            }
        }
    }
}