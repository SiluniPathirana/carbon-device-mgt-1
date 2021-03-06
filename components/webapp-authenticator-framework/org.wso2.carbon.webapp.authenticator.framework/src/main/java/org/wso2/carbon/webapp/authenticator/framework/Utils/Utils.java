/*
 * Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.webapp.authenticator.framework.Utils;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.user.api.TenantManager;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.user.core.service.RealmService;
import org.wso2.carbon.utils.multitenancy.MultitenantUtils;
import org.wso2.carbon.webapp.authenticator.framework.AuthenticationException;
import org.wso2.carbon.webapp.authenticator.framework.AuthenticationInfo;
import org.wso2.carbon.webapp.authenticator.framework.authenticator.WebappAuthenticator;
import org.wso2.carbon.webapp.authenticator.framework.authenticator.oauth.OAuth2TokenValidator;
import org.wso2.carbon.webapp.authenticator.framework.authenticator.oauth.OAuthValidationResponse;
import org.wso2.carbon.webapp.authenticator.framework.authenticator.oauth.OAuthValidatorFactory;
import org.wso2.carbon.webapp.authenticator.framework.internal.AuthenticatorFrameworkDataHolder;

import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {
    private static final Log log = LogFactory.getLog(Utils.class);

    public static int getTenantIdOFUser(String username) throws AuthenticationException {
        int tenantId = 0;
        String domainName = MultitenantUtils.getTenantDomain(username);
        if (domainName != null) {
            try {
                TenantManager tenantManager = AuthenticatorFrameworkDataHolder.getInstance().getRealmService()
                        .getTenantManager();
                tenantId = tenantManager.getTenantId(domainName);
            } catch (UserStoreException e) {
                String errorMsg = "Error when getting the tenant id from the tenant domain : " +
                        domainName;
                log.error(errorMsg, e);
                throw new AuthenticationException(errorMsg, e);
            }
        }
        return tenantId;
    }

    public static String getTenantDomain(int tenantId) throws AuthenticationException {
        try {
            PrivilegedCarbonContext.startTenantFlow();
            RealmService realmService = AuthenticatorFrameworkDataHolder.getInstance().getRealmService();
            if (realmService == null) {
                String msg = "RealmService is not initialized";
                log.error(msg);
                throw new AuthenticationException(msg);
            }

            return realmService.getTenantManager().getDomain(tenantId);

        } catch (UserStoreException e) {
            String msg = "User store not initialized";
            log.error(msg);
            throw new AuthenticationException(msg, e);
        } finally {
            PrivilegedCarbonContext.endTenantFlow();
        }
    }

    /**
     * To init BST and Oauth authenticators
     *
     * @param properties Properties of authenticators
     * @return token validator, if all the required parameters satisfied
     */
    public static OAuth2TokenValidator initAuthenticators(Properties properties) {
        if (properties == null) {
            throw new IllegalArgumentException(
                    "Required properties needed to initialize OAuthAuthenticator are not provided");
        }
        String tokenValidationEndpointUrl = properties.getProperty("TokenValidationEndpointUrl");
        if (tokenValidationEndpointUrl == null || tokenValidationEndpointUrl.isEmpty()) {
            throw new IllegalArgumentException("OAuth token validation endpoint url is not provided");
        }
        String url = Utils.replaceSystemProperty(tokenValidationEndpointUrl);
        if ((url == null) || (url.isEmpty())) {
            throw new IllegalArgumentException("OAuth token validation endpoint url is not provided");
        }
        String adminUsername = properties.getProperty("Username");
        if (adminUsername == null) {
            throw new IllegalArgumentException(
                    "Username to connect to the OAuth token validation endpoint is not provided");
        }
        String adminPassword = properties.getProperty("Password");
        if (adminPassword == null) {
            throw new IllegalArgumentException(
                    "Password to connect to the OAuth token validation endpoint is not provided");
        }
        boolean isRemote = Boolean.parseBoolean(properties.getProperty("IsRemote"));
        Properties validatorProperties = new Properties();
        String maxTotalConnections = properties.getProperty("MaxTotalConnections");
        String maxConnectionsPerHost = properties.getProperty("MaxConnectionsPerHost");
        if (maxTotalConnections != null) {
            validatorProperties.setProperty("MaxTotalConnections", maxTotalConnections);
        }
        if (maxConnectionsPerHost != null) {
            validatorProperties.setProperty("MaxConnectionsPerHost", maxConnectionsPerHost);
        }
        return OAuthValidatorFactory.getValidator(url, adminUsername, adminPassword, isRemote, validatorProperties);
    }

    /**
     * To set the authentication info based on the OauthValidationResponse.
     *
     * @return Updated Authentication info based on OauthValidationResponse
     */
    public static AuthenticationInfo setAuthenticationInfo(OAuthValidationResponse oAuthValidationResponse,
            AuthenticationInfo authenticationInfo) throws AuthenticationException {
        if (oAuthValidationResponse.isValid()) {
            String username = oAuthValidationResponse.getUserName();
            String tenantDomain = oAuthValidationResponse.getTenantDomain();
            authenticationInfo.setUsername(username);
            authenticationInfo.setTenantDomain(tenantDomain);
            authenticationInfo.setTenantId(getTenantIdOFUser(username + "@" + tenantDomain));
            authenticationInfo.setStatus(WebappAuthenticator.Status.CONTINUE);
        } else {
            authenticationInfo.setMessage(oAuthValidationResponse.getErrorMsg());
            authenticationInfo.setStatus(WebappAuthenticator.Status.FAILURE);
        }
        return authenticationInfo;
    }

    private static String replaceSystemProperty(String urlWithPlaceholders)  {
        String regex = "\\$\\{(.*?)\\}";
        Pattern pattern = Pattern.compile(regex);
        Matcher matchPattern = pattern.matcher(urlWithPlaceholders);
        while (matchPattern.find()) {
            String sysPropertyName = matchPattern.group(1);
            String sysPropertyValue = System.getProperty(sysPropertyName);
            if (sysPropertyValue != null && !sysPropertyName.isEmpty()) {
                urlWithPlaceholders = urlWithPlaceholders.replaceAll("\\$\\{(" + sysPropertyName + ")\\}", sysPropertyValue);
            }
        }
        return urlWithPlaceholders;
    }

}
