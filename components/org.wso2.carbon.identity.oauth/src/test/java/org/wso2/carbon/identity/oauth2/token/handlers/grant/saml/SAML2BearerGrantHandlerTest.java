/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.identity.oauth2.token.handlers.grant.saml;

import com.google.gdata.util.common.base.Charsets;
import org.apache.commons.codec.binary.Base64;
import org.joda.time.DateTime;
import org.mockito.Mock;
import org.opensaml.saml2.core.Assertion;
import org.opensaml.security.SAMLSignatureProfileValidator;
import org.opensaml.xml.security.x509.X509Credential;
import org.opensaml.xml.signature.Signature;
import org.opensaml.xml.signature.SignatureValidator;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.carbon.identity.application.authentication.framework.model.AuthenticatedUser;
import org.wso2.carbon.identity.application.common.model.Claim;
import org.wso2.carbon.identity.application.common.model.ClaimMapping;
import org.wso2.carbon.identity.application.common.model.FederatedAuthenticatorConfig;
import org.wso2.carbon.identity.application.common.model.IdentityProvider;
import org.wso2.carbon.identity.application.common.model.LocalAndOutboundAuthenticationConfig;
import org.wso2.carbon.identity.application.common.model.Property;
import org.wso2.carbon.identity.application.common.model.ServiceProvider;
import org.wso2.carbon.identity.application.common.util.IdentityApplicationConstants;
import org.wso2.carbon.identity.application.common.util.IdentityApplicationManagementUtil;
import org.wso2.carbon.identity.application.mgt.ApplicationManagementService;
import org.wso2.carbon.identity.core.model.SAMLSSOServiceProviderDO;
import org.wso2.carbon.identity.core.util.IdentityTenantUtil;
import org.wso2.carbon.identity.core.util.IdentityUtil;
import org.wso2.carbon.identity.oauth.common.OAuthConstants;
import org.wso2.carbon.identity.oauth.config.OAuthServerConfiguration;
import org.wso2.carbon.identity.oauth.internal.OAuthComponentServiceHolder;
import org.wso2.carbon.identity.oauth2.TestConstants;
import org.wso2.carbon.identity.oauth2.dto.OAuth2AccessTokenReqDTO;
import org.wso2.carbon.identity.oauth2.internal.OAuth2ServiceComponentHolder;
import org.wso2.carbon.identity.oauth2.token.OAuthTokenReqMessageContext;
import org.wso2.carbon.identity.oauth2.token.OauthTokenIssuer;
import org.wso2.carbon.identity.oauth2.util.OAuth2Util;
import org.wso2.carbon.identity.sso.saml.SSOServiceProviderConfigManager;
import org.wso2.carbon.identity.sso.saml.dto.SAMLSSOAuthnReqDTO;
import org.wso2.carbon.identity.sso.saml.util.SAMLSSOUtil;
import org.wso2.carbon.idp.mgt.IdentityProviderManager;
import org.wso2.carbon.user.core.UserRealm;
import org.wso2.carbon.user.core.UserStoreManager;
import org.wso2.carbon.user.core.service.RealmService;
import org.wso2.carbon.user.core.tenant.TenantManager;
import org.wso2.carbon.utils.multitenancy.MultitenantUtils;

import java.lang.reflect.Field;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Matchers.*;
import static org.powermock.api.mockito.PowerMockito.*;

@PowerMockIgnore({"javax.net.*"})
@PrepareForTest({IdentityUtil.class, IdentityTenantUtil.class, IdentityProviderManager.class, OAuth2Util.class,
        IdentityProvider.class, IdentityApplicationManagementUtil.class, OAuthServerConfiguration.class,
        OAuth2AccessTokenReqDTO.class, SSOServiceProviderConfigManager.class, SAML2BearerGrantHandler.class,
        OAuthComponentServiceHolder.class, OAuth2ServiceComponentHolder.class, MultitenantUtils.class})
public class SAML2BearerGrantHandlerTest extends PowerMockTestCase {

    private SAML2BearerGrantHandler saml2BearerGrantHandler;

    private Assertion assertion;

    private  ServiceProvider serviceProvider;

    @Mock
    private OauthTokenIssuer oauthIssuer;

    @Mock
    private OAuthComponentServiceHolder oAuthComponentServiceHolder;

    @Mock
    private RealmService realmService;

    @Mock
    private TenantManager tenantManager;

    @Mock
    private IdentityProviderManager identityProviderManager;

    @Mock
    private IdentityProvider identityProvider;

    @Mock
    private FederatedAuthenticatorConfig federatedAuthenticatorConfig;

    @Mock
    private SSOServiceProviderConfigManager ssoServiceProviderConfigManager;

    @Mock
    private OAuthTokenReqMessageContext tokReqMsgCtx;

    @Mock
    private OAuth2AccessTokenReqDTO oAuth2AccessTokenReqDTO;

    @Mock
    private OAuthServerConfiguration oAuthServerConfiguration;

    @Mock
    private SAMLSignatureProfileValidator profileValidator;

    @Mock
    private X509Certificate x509Certificate;

    @Mock
    private SignatureValidator signatureValidator;

    @Mock
    private UserStoreManager userStoreManager;

    @Mock
    private UserRealm userRealm;


    @Mock
    private ApplicationManagementService applicationManagementService;
    @BeforeMethod
    public void setUp() throws Exception {

        mockStatic(OAuthServerConfiguration.class);
        when(OAuthServerConfiguration.getInstance()).thenReturn(oAuthServerConfiguration);
        when(oAuthServerConfiguration.getIdentityOauthTokenIssuer()).thenReturn(oauthIssuer);
        saml2BearerGrantHandler = new SAML2BearerGrantHandler();
        saml2BearerGrantHandler.init();
        assertion = buildAssertion();
    }

    @Test
    public void testValidateGrant() throws Exception {

        oAuth2AccessTokenReqDTO = new OAuth2AccessTokenReqDTO();
        saml2BearerGrantHandler = new SAML2BearerGrantHandler();
        tokReqMsgCtx = new OAuthTokenReqMessageContext(oAuth2AccessTokenReqDTO);
        tokReqMsgCtx.setTenantID(-1234);
        String assertionString = SAMLSSOUtil.marshall(assertion);
        assertionString = new String(Base64.encodeBase64(assertionString.getBytes(Charsets.UTF_8)));
        oAuth2AccessTokenReqDTO.setAssertion(assertionString);
        when(IdentityUtil.unmarshall(anyString())).thenReturn(assertion);
        Assert.assertFalse(saml2BearerGrantHandler.validateGrant(tokReqMsgCtx));

        mockStatic(IdentityProviderManager.class);
        mockStatic(IdentityProvider.class);
        mockStatic(OAuthServerConfiguration.class);
        mockStatic(IdentityApplicationManagementUtil.class);
        IdentityProvider identityPro = new IdentityProvider();
        identityPro.setIdentityProviderName("LOCAL");
        when(IdentityProviderManager.getInstance()).thenReturn(identityProviderManager);
        when(identityProviderManager
                .getIdPByAuthenticatorPropertyValue(anyString(), anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn(identityPro);

        FederatedAuthenticatorConfig oauthConfig = new FederatedAuthenticatorConfig();
        FederatedAuthenticatorConfig samlConfig = new FederatedAuthenticatorConfig();

        Property property = new Property();
        property.setName("OAuth2TokenEPUrl");
        property.setValue(TestConstants.LOACALHOST_DOMAIN);
        Property[] properties = {property};
        oauthConfig.setProperties(properties);

        Property property1 = new Property();
        property1.setName("samlsso");
        property1.setValue(TestConstants.LOACALHOST_DOMAIN);
        Property[] properties1 = {property1};
        samlConfig.setProperties(properties1);

        Property property2 = new Property();
        property2.setName(IdentityApplicationConstants.Authenticator.SAML2SSO.IDP_ENTITY_ID);
        property2.setValue(TestConstants.LOACALHOST_DOMAIN);
        Property[] properties2 = {property2};

        FederatedAuthenticatorConfig[] fedAuthConfs = {federatedAuthenticatorConfig};
        when(federatedAuthenticatorConfig.getProperties()).thenReturn(properties2);
        when(federatedAuthenticatorConfig.getName()).thenReturn(
                IdentityApplicationConstants.Authenticator.SAML2SSO.NAME);
        when(identityProvider.getFederatedAuthenticatorConfigs()).thenReturn(fedAuthConfs);

        when(IdentityApplicationManagementUtil.getFederatedAuthenticator(fedAuthConfs, "samlsso"))
                .thenReturn(samlConfig);
        when(IdentityApplicationManagementUtil.getFederatedAuthenticator(fedAuthConfs, "openidconnect"))
                .thenReturn(oauthConfig);
        when(IdentityApplicationManagementUtil.getProperty(samlConfig.getProperties(), "IdPEntityId"))
                .thenReturn(property1);
        when(IdentityApplicationManagementUtil.getProperty(oauthConfig.getProperties(), "OAuth2TokenEPUrl"))
                .thenReturn(property);

        when(OAuthServerConfiguration.getInstance()).thenReturn(oAuthServerConfiguration);
        when(oAuthServerConfiguration.getTimeStampSkewInSeconds()).thenReturn(-1000000000000000L);
        Assert.assertFalse(saml2BearerGrantHandler.validateGrant(tokReqMsgCtx));

        when(oAuthServerConfiguration.getTimeStampSkewInSeconds()).thenReturn(1000000000000000L);

        Field field=SAML2BearerGrantHandler.class.getDeclaredField("profileValidator");
        field.setAccessible(true);
        field.set(saml2BearerGrantHandler,profileValidator);
        field.setAccessible(false);
        doNothing().when(profileValidator).validate(any(Signature.class));

        Certificate certificate1 =  x509Certificate;
        when(IdentityApplicationManagementUtil.decodeCertificate(anyString()))
                .thenReturn(certificate1);

        whenNew(SignatureValidator.class).withArguments(any(X509Credential.class)).thenReturn(signatureValidator);
        doNothing().when(signatureValidator).validate(any(Signature.class));

        Assert.assertTrue(saml2BearerGrantHandler.validateGrant(tokReqMsgCtx));

        property1.setValue(null);
        Assert.assertFalse(saml2BearerGrantHandler.validateGrant(tokReqMsgCtx));

        property1.setValue("notLocalHost");
        Assert.assertFalse(saml2BearerGrantHandler.validateGrant(tokReqMsgCtx));

        property2.setValue("notLocal");
        Assert.assertFalse(saml2BearerGrantHandler.validateGrant(tokReqMsgCtx));

        when(identityProviderManager
                .getIdPByAuthenticatorPropertyValue(anyString(), anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn(null);
        Assert.assertFalse(saml2BearerGrantHandler.validateGrant(tokReqMsgCtx));

        when(identityProviderManager
                .getIdPByAuthenticatorPropertyValue("IdPEntityId", "localhost", "carbon.super", "samlsso", false))
                .thenReturn(null);
        Assert.assertFalse(saml2BearerGrantHandler.validateGrant(tokReqMsgCtx));

        when(identityProviderManager
                .getIdPByAuthenticatorPropertyValue("IdPEntityId", "localhost", "carbon.super", "SAMLSSOAuthenticator", false))
                .thenReturn(null);
        Assert.assertFalse(saml2BearerGrantHandler.validateGrant(tokReqMsgCtx));

        property.setName("TokenEPUrl");
        property.setValue("notLocal");
        Assert.assertFalse(saml2BearerGrantHandler.validateGrant(tokReqMsgCtx));

        when(IdentityApplicationManagementUtil.getProperty(samlConfig.getProperties(), "IdPEntityId"))
                .thenReturn(null);
        Assert.assertFalse(saml2BearerGrantHandler.validateGrant(tokReqMsgCtx));

        when(IdentityApplicationManagementUtil.getProperty(oauthConfig.getProperties(), "OAuth2TokenEPUrl"))
                .thenReturn(null);
        Assert.assertFalse(saml2BearerGrantHandler.validateGrant(tokReqMsgCtx));

        identityPro.setAlias("");
        Assert.assertFalse(saml2BearerGrantHandler.validateGrant(tokReqMsgCtx));

    }

    @Test
    public void testIssueRefreshToken() throws Exception {

        when(oAuthServerConfiguration.getValueForIsRefreshTokenAllowed(OAuthConstants.OAUTH_SAML2_BEARER_METHOD)).thenReturn(true);
        Assert.assertTrue(saml2BearerGrantHandler.issueRefreshToken());
    }

    @Test
    public void testSetUser() throws Exception {

        oAuth2AccessTokenReqDTO = new OAuth2AccessTokenReqDTO();
        saml2BearerGrantHandler = new SAML2BearerGrantHandler();
        tokReqMsgCtx = new OAuthTokenReqMessageContext(oAuth2AccessTokenReqDTO);
        tokReqMsgCtx.setTenantID(-1234);
        when(oAuthServerConfiguration.getSaml2BearerTokenUserType()).thenReturn(OAuthConstants.UserType.FEDERATED_USER_DOMAIN_PREFIX);
        saml2BearerGrantHandler.setUser(tokReqMsgCtx,identityProvider,assertion,TestConstants.CARBON_TENANT_DOMAIN);
        Assert.assertEquals(tokReqMsgCtx.getAuthorizedUser().getUserName(),assertion.getSubject().getNameID().getValue());

        when(oAuthServerConfiguration.getSaml2BearerTokenUserType()).thenReturn(OAuthConstants.UserType.LOCAL_USER_TYPE);
        serviceProvider = getServiceprovider(false,false);
        mockStatic(OAuthComponentServiceHolder.class);
        when(OAuthComponentServiceHolder.getInstance()).thenReturn(oAuthComponentServiceHolder);
        when(oAuthComponentServiceHolder.getRealmService()).thenReturn(realmService);
        mockStatic(OAuth2ServiceComponentHolder.class);
        when(OAuth2ServiceComponentHolder.getApplicationMgtService()).thenReturn(applicationManagementService);
        when(applicationManagementService.getServiceProviderByClientId(anyString(),anyString(),anyString()))
                .thenReturn(serviceProvider);
        when(realmService.getTenantUserRealm(anyInt())).thenReturn(userRealm);
        when(userRealm.getUserStoreManager()).thenReturn(userStoreManager);
        when(userStoreManager.isExistingUser(anyString())).thenReturn(true);
        saml2BearerGrantHandler.setUser(tokReqMsgCtx,identityProvider,assertion,TestConstants.CARBON_TENANT_DOMAIN);
        Assert.assertEquals(tokReqMsgCtx.getAuthorizedUser().getUserName(),assertion.getSubject().getNameID().getValue());

        when(oAuthServerConfiguration.getSaml2BearerTokenUserType()).thenReturn("notValid");
        when(identityProvider.getIdentityProviderName()).thenReturn(IdentityApplicationConstants.RESIDENT_IDP_RESERVED_NAME);
        saml2BearerGrantHandler.setUser(tokReqMsgCtx,identityProvider,assertion,TestConstants.CARBON_TENANT_DOMAIN);
        Assert.assertEquals(tokReqMsgCtx.getAuthorizedUser().getUserName(),assertion.getSubject().getNameID().getValue());

        when(identityProvider.getIdentityProviderName()).thenReturn("notLocal");
        saml2BearerGrantHandler.setUser(tokReqMsgCtx,identityProvider,assertion,TestConstants.CARBON_TENANT_DOMAIN);
        Assert.assertEquals(tokReqMsgCtx.getAuthorizedUser().getUserName(),assertion.getSubject().getNameID().getValue());

        mockStatic(OAuth2Util.class);
        AuthenticatedUser authenticatedUser = new AuthenticatedUser();
        authenticatedUser.setAuthenticatedSubjectIdentifier(assertion.getSubject().getNameID().getValue());
        when(OAuth2Util.getUserFromUserName(anyString())).thenReturn(authenticatedUser);
        when(oAuthServerConfiguration.getSaml2BearerTokenUserType()).thenReturn(OAuthConstants.UserType.LEGACY_USER_TYPE);
        saml2BearerGrantHandler.setUser(tokReqMsgCtx,identityProvider,assertion,TestConstants.CARBON_TENANT_DOMAIN);
        String subject = tokReqMsgCtx.getAuthorizedUser().getAuthenticatedSubjectIdentifier();
        Assert.assertEquals(subject, authenticatedUser.getAuthenticatedSubjectIdentifier());
    }

    @Test
    public void testBuildLocalUser() throws Exception {

        tokReqMsgCtx = new OAuthTokenReqMessageContext(oAuth2AccessTokenReqDTO);
        saml2BearerGrantHandler = new SAML2BearerGrantHandler();
        serviceProvider = getServiceprovider(false,true);
        AuthenticatedUser authenticatedUser = saml2BearerGrantHandler.buildLocalUser(tokReqMsgCtx, assertion,
                serviceProvider, TestConstants.CARBON_TENANT_DOMAIN);
        Assert.assertEquals(authenticatedUser.getUserName(), TestConstants.TEST_USER_NAME);

        mockStatic(MultitenantUtils.class);
        when(MultitenantUtils.getTenantDomain(anyString())).thenReturn("");
        authenticatedUser = saml2BearerGrantHandler.buildLocalUser(tokReqMsgCtx, assertion,
                serviceProvider, TestConstants.CARBON_TENANT_DOMAIN);
        Assert.assertEquals(authenticatedUser.getTenantDomain(), TestConstants.CARBON_TENANT_DOMAIN);
    }

    private ServiceProvider getServiceprovider(boolean isTenantDomainInSubject, boolean isUserstoreDomainInSubject){

        serviceProvider = new ServiceProvider();
        serviceProvider.setSaasApp(true);
        LocalAndOutboundAuthenticationConfig localAndOutboundAuthenticationConfig
                = new LocalAndOutboundAuthenticationConfig();
        localAndOutboundAuthenticationConfig.setUseTenantDomainInLocalSubjectIdentifier(isTenantDomainInSubject);
        localAndOutboundAuthenticationConfig.setUseUserstoreDomainInLocalSubjectIdentifier(isUserstoreDomainInSubject);
        serviceProvider.setLocalAndOutBoundAuthenticationConfig(localAndOutboundAuthenticationConfig);
        return serviceProvider;
    }

    @Test
    public void testCreateLegacyUser() throws Exception {

        mockStatic(OAuth2Util.class);
        AuthenticatedUser authenticatedUser = new AuthenticatedUser();
        authenticatedUser.setAuthenticatedSubjectIdentifier(assertion.getSubject().getNameID().getValue());
        when(OAuth2Util.getUserFromUserName(anyString())).thenReturn(authenticatedUser);
        tokReqMsgCtx = new OAuthTokenReqMessageContext(new OAuth2AccessTokenReqDTO());
        saml2BearerGrantHandler.createLegacyUser(tokReqMsgCtx, assertion);
        String subject = tokReqMsgCtx.getAuthorizedUser().getAuthenticatedSubjectIdentifier();
        Assert.assertEquals(subject, authenticatedUser.getAuthenticatedSubjectIdentifier());
    }

    private void prepareForGetIssuer() throws Exception {

        when(tenantManager.getTenantId(anyString())).thenReturn(-1234);
        when(realmService.getTenantManager()).thenReturn(tenantManager);
        SAMLSSOUtil.setRealmService(realmService);

        Property property = new Property();
        property.setName(IdentityApplicationConstants.Authenticator.SAML2SSO.IDP_ENTITY_ID);
        property.setValue(TestConstants.LOACALHOST_DOMAIN);
        Property[] properties = {property};
        when(federatedAuthenticatorConfig.getProperties()).thenReturn(properties);
        when(federatedAuthenticatorConfig.getName()).thenReturn(
                IdentityApplicationConstants.Authenticator.SAML2SSO.NAME);
        FederatedAuthenticatorConfig[] fedAuthConfs = {federatedAuthenticatorConfig};
        when(identityProvider.getFederatedAuthenticatorConfigs()).thenReturn(fedAuthConfs);

        mockStatic(IdentityProviderManager.class);
        when(IdentityProviderManager.getInstance()).thenReturn(identityProviderManager);
        when(identityProviderManager.getResidentIdP(anyString())).thenReturn(identityProvider);
    }

    private void prepareForUserAttributes(String attrConsumerIndex, String issuer, String spName) {

        mockStatic(SSOServiceProviderConfigManager.class);
        when(SSOServiceProviderConfigManager.getInstance()).thenReturn(ssoServiceProviderConfigManager);
        SAMLSSOServiceProviderDO samlssoServiceProviderDO = new SAMLSSOServiceProviderDO();
        samlssoServiceProviderDO.setAttributeConsumingServiceIndex(attrConsumerIndex);
        samlssoServiceProviderDO.setEnableAttributesByDefault(true);
        samlssoServiceProviderDO.setIssuer(issuer);
        ssoServiceProviderConfigManager.addServiceProvider(issuer, samlssoServiceProviderDO);
        when(ssoServiceProviderConfigManager.getServiceProvider(spName)).thenReturn(samlssoServiceProviderDO);
    }

    private Assertion buildAssertion() throws Exception {

        prepareForGetIssuer();
        mockStatic(IdentityUtil.class);
        mockStatic(IdentityTenantUtil.class);
        when(IdentityUtil.getServerURL(anyString(), anyBoolean(), anyBoolean()))
                .thenReturn(TestConstants.SAMPLE_SERVER_URL);
        prepareForUserAttributes(TestConstants.ATTRIBUTE_CONSUMER_INDEX, TestConstants.LOACALHOST_DOMAIN,
                TestConstants.LOACALHOST_DOMAIN);
        Map<String, String> inputAttributes = new HashMap<>();
        inputAttributes.put(TestConstants.CLAIM_URI1, TestConstants.CLAIM_VALUE1);
        inputAttributes.put(TestConstants.CLAIM_URI2, TestConstants.CLAIM_VALUE2);
        SAMLSSOAuthnReqDTO authnReqDTO = buildAuthnReqDTO(inputAttributes, TestConstants.SAMPLE_NAME_ID_FORMAT,
                TestConstants.LOACALHOST_DOMAIN, TestConstants.TEST_USER_NAME);
        authnReqDTO.setNameIDFormat(TestConstants.SAMPLE_NAME_ID_FORMAT);
        authnReqDTO.setIssuer(TestConstants.LOACALHOST_DOMAIN);
        assertion = SAMLSSOUtil.buildSAMLAssertion(authnReqDTO, new DateTime(System.currentTimeMillis() + 10000000L),
                TestConstants.SESSION_ID);
        return assertion;
    }

    private ClaimMapping buildClaimMapping(String claimUri) {

        ClaimMapping claimMapping = new ClaimMapping();
        Claim claim = new Claim();
        claim.setClaimUri(claimUri);
        claimMapping.setRemoteClaim(claim);
        claimMapping.setLocalClaim(claim);
        return claimMapping;
    }

    private SAMLSSOAuthnReqDTO buildAuthnReqDTO(Map<String, String> attributes, String nameIDFormat, String issuer,
                                                String subjectName) {

        SAMLSSOAuthnReqDTO authnReqDTO = new SAMLSSOAuthnReqDTO();
        authnReqDTO.setUser(AuthenticatedUser.createFederateAuthenticatedUserFromSubjectIdentifier(subjectName));
        authnReqDTO.setNameIDFormat(nameIDFormat);
        authnReqDTO.setIssuer(issuer);
        Map<ClaimMapping, String> userAttributes = new HashMap<>();

        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            userAttributes.put(buildClaimMapping(entry.getKey()), entry.getValue());
        }
        authnReqDTO.getUser().setUserAttributes(userAttributes);
        return authnReqDTO;
    }

}


