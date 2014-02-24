/*
 * Copyright (C) 2003-2013 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.services.videocall;

import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.HashMap;

import javax.jcr.LoginException;
import javax.jcr.NoSuchWorkspaceException;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.model.videocall.VideoCallModel;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.services.cache.CacheService;
import org.exoplatform.services.cache.ExoCache;
import org.exoplatform.services.cms.mimetype.DMSMimeTypeResolver;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.access.PermissionType;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.wcm.core.NodetypeConstant;
import org.exoplatform.services.wcm.utils.WCMCoreUtils;
import org.apache.commons.lang.StringUtils;


public class VideoCallService {
  private static ExoCache<Serializable, VideoCallModel> videoProfileCache;
  public static String VIDEO_PROFILE_KEY = "videoCallsProfile" + CommonsUtils.getRepository().getConfiguration().getName();
  public static String BASE_PATH = "exo:applications";
  public static String VIDEOCALL_BASE_PATH = "VideoCallsProfile";
  public static String VIDEOCALL_NODETYPE = "exo:videoCallProfile";
  public static String DISABLEVIDEOCALL_PROP ="exo:disableVideoCall";
  public static String WEEMOKEY_PROP = "exo:weemoKey";
  public static String VIDEO_PERMISSIONS_PROP = "exo:videoCallPermissions";
  public static String VIDEO_TOKEN_KEY = "exo:tokenKey";
  public static String VIDEO_PASSPHARSE = "exo:passPhrase";
  public static String VIDEO_AUTH_ID = "exo:authId";
  public static String VIDEO_AUTH_SECRET = "exo:authSecret";
  public static String VIDEO_P12_CERT_NODE_NAME = "p12Cert";
  public static String VIDEO_PEM_CERT_NODE_NAME = "pemCert";
  public static String VIDEO_PROFILE_ID = "exo:profileId";
  public static String VIDEO_DOMAIN_ID = "exo:domainId";
  
  private static final Log LOG = ExoLogger.getLogger(VideoCallService.class.getName());
  
  protected static final String WORKSPACE_NAME = "collaboration";
  
  public VideoCallService() {
    videoProfileCache = WCMCoreUtils.getService(CacheService.class).getCacheInstance(VideoCallService.class.getName());
  }
  
  public void saveVideoCallProfile(VideoCallModel videoCallModel) {
    String disbaleVideoCall = videoCallModel.getDisableVideoCall();
    String weemoKey = videoCallModel.getWeemoKey();
    String videoCallPermissions = videoCallModel.getVideoCallPermissions(); 
    String passPharse = videoCallModel.getCustomerCertificatePassphrase();
    String authId = videoCallModel.getAuthId();
    String authSecret = videoCallModel.getAuthSecret();
    InputStream p12Cert = videoCallModel.getP12Cert();
    InputStream pemCert = videoCallModel.getPemCert();
    String profileId = videoCallModel.getProfileId();
    String domainId = videoCallModel.getDomainId();
   
    SessionProvider sessionProvider = null;
    try {
      sessionProvider = WCMCoreUtils.getSystemSessionProvider();
      RepositoryService repositoryService = WCMCoreUtils.getService(RepositoryService.class);
      Session session = sessionProvider.getSession(WORKSPACE_NAME, repositoryService.getCurrentRepository());
      
      Node rootNode = session.getRootNode();
      Node baseNode = rootNode.getNode(BASE_PATH);
      Node videoCallNode = null;
      if(baseNode.hasNode(VIDEOCALL_BASE_PATH)) {
        videoCallNode = baseNode.getNode(VIDEOCALL_BASE_PATH);
      } else {
        videoCallNode = baseNode.addNode(VIDEOCALL_BASE_PATH, VIDEOCALL_NODETYPE);        
      }
      videoCallNode.setProperty(DISABLEVIDEOCALL_PROP, Boolean.valueOf(disbaleVideoCall));
      videoCallNode.setProperty(WEEMOKEY_PROP, weemoKey);
      videoCallNode.setProperty(VIDEO_TOKEN_KEY, videoCallModel.getTokenKey());
      videoCallNode.setProperty(VIDEO_PERMISSIONS_PROP, videoCallPermissions);
      videoCallNode.setProperty(VIDEO_PASSPHARSE, passPharse);
      videoCallNode.setProperty(VIDEO_AUTH_ID, authId);
      videoCallNode.setProperty(VIDEO_AUTH_SECRET, authSecret);
      if(StringUtils.isNotEmpty(profileId)) videoCallNode.setProperty(VIDEO_PROFILE_ID, profileId);
      if(StringUtils.isNotEmpty(domainId)) videoCallNode.setProperty(VIDEO_DOMAIN_ID, domainId);
      // Update p12 certificate file
      if(p12Cert != null) {
        Node p12CertNode = null;
        if(videoCallNode.hasNode(VIDEO_P12_CERT_NODE_NAME)) {
          p12CertNode = videoCallNode.getNode(VIDEO_P12_CERT_NODE_NAME);
        } else {
          p12CertNode = videoCallNode.addNode(VIDEO_P12_CERT_NODE_NAME, NodetypeConstant.NT_FILE);
        }
        Node jcrContent = null;
        if(p12CertNode.hasNode(NodetypeConstant.JCR_CONTENT)) {
          jcrContent = p12CertNode.getNode(NodetypeConstant.JCR_CONTENT);
        } else {
          jcrContent = p12CertNode.addNode(NodetypeConstant.JCR_CONTENT, NodetypeConstant.NT_RESOURCE);
        }
        DMSMimeTypeResolver mimeTypeResolver = DMSMimeTypeResolver.getInstance();
        String mimetype = mimeTypeResolver.getMimeType(videoCallModel.getP12CertName());
        jcrContent.setProperty("jcr:data", p12Cert);
        jcrContent.setProperty("jcr:lastModified",new GregorianCalendar());
        jcrContent.setProperty("jcr:mimeType",mimetype);
      }
      
      // Update pem certificate file
      if(pemCert != null) {
        Node pemCertNode = null;
        if(videoCallNode.hasNode(VIDEO_PEM_CERT_NODE_NAME)) {
          pemCertNode = videoCallNode.getNode(VIDEO_PEM_CERT_NODE_NAME);
        } else {
          pemCertNode = videoCallNode.addNode(VIDEO_PEM_CERT_NODE_NAME, NodetypeConstant.NT_FILE);
        }
        Node jcrContent = null;
        if(pemCertNode.hasNode(NodetypeConstant.JCR_CONTENT)) {
          jcrContent = pemCertNode.getNode(NodetypeConstant.JCR_CONTENT);
        } else {
          jcrContent = pemCertNode.addNode(NodetypeConstant.JCR_CONTENT, NodetypeConstant.NT_RESOURCE);
        }
        DMSMimeTypeResolver mimeTypeResolver = DMSMimeTypeResolver.getInstance();
        String mimetype = mimeTypeResolver.getMimeType(videoCallModel.getPemCertName());
        jcrContent.setProperty("jcr:data", pemCert);
        jcrContent.setProperty("jcr:lastModified",new GregorianCalendar());
        jcrContent.setProperty("jcr:mimeType",mimetype);
      }
      ExtendedNode node = (ExtendedNode) videoCallNode;
      if (node.canAddMixin("exo:privilegeable")) { 
        node.addMixin("exo:privilegeable");
        node.setPermission("*:/platform/users",new String[] { PermissionType.READ });
      }
      session.save();  
      videoProfileCache.put(VIDEO_PROFILE_KEY, videoCallModel);
    } catch (LoginException e) {
      if (LOG.isErrorEnabled()) {
        LOG.error("saveVideoCallProfile() failed because of ", e);
      }
    } catch (NoSuchWorkspaceException e) {
      if (LOG.isErrorEnabled()) {
        LOG.error("saveVideoCallProfile() failed because of ", e);
      }
    } catch (RepositoryException e) {
      if (LOG.isErrorEnabled()) {
        LOG.error("saveVideoCallProfile() failed because of ", e);
      }
    } catch (Exception e) {
      if (LOG.isErrorEnabled()) {
        LOG.error("saveVideoCallProfile() failed because of ", e);
      }
    } 
  }
  
  public VideoCallModel getVideoCallProfile() {
    VideoCallModel videoCallModel = null;
    if(videoProfileCache != null && videoProfileCache.get(VIDEO_PROFILE_KEY) != null) {
      videoCallModel = videoProfileCache.get(VIDEO_PROFILE_KEY);
    } else {
      SessionProvider sessionProvider = null;
      RepositoryService repositoryService = WCMCoreUtils.getService(RepositoryService.class);
      if(repositoryService == null) return null;
      sessionProvider = WCMCoreUtils.getUserSessionProvider();
      Session session;
      try {
        session = sessionProvider.getSession(WORKSPACE_NAME, repositoryService.getCurrentRepository());    
        Node rootNode = session.getRootNode();
        Node baseNode = rootNode.getNode(BASE_PATH);
        if(baseNode.hasNode(VIDEOCALL_BASE_PATH)) {
          Node videoCallNode = baseNode.getNode(VIDEOCALL_BASE_PATH);
          videoCallModel = new VideoCallModel();
          videoCallModel.setWeemoKey(videoCallNode.getProperty(WEEMOKEY_PROP).getString());
          videoCallModel.setDisableVideoCall(videoCallNode.getProperty(DISABLEVIDEOCALL_PROP).getString());
          videoCallModel.setTokenKey(videoCallNode.getProperty(VIDEO_TOKEN_KEY).getString());
          videoCallModel.setProfileId(videoCallNode.getProperty(VIDEO_PROFILE_ID).getString());
          videoCallModel.setDomainId(videoCallNode.getProperty(VIDEO_DOMAIN_ID).getString());
          if(videoCallNode.hasProperty(VIDEO_PERMISSIONS_PROP)) {
            videoCallModel.setVideoCallPermissions(videoCallNode.getProperty(VIDEO_PERMISSIONS_PROP).getString());
          }          
          videoCallModel.setCustomerCertificatePassphrase(videoCallNode.getProperty(VIDEO_PASSPHARSE).getString());
          videoCallModel.setAuthId(videoCallNode.getProperty(VIDEO_AUTH_ID).getString());
          videoCallModel.setAuthSecret(videoCallNode.getProperty(VIDEO_AUTH_SECRET).getString());
          if(videoCallNode.hasNode(VIDEO_P12_CERT_NODE_NAME)) {
            Node p12CertNode = videoCallNode.getNode(VIDEO_P12_CERT_NODE_NAME);
            Node jcrContent = p12CertNode.getNode(NodetypeConstant.JCR_CONTENT);
            if(jcrContent != null && jcrContent.getProperty(NodetypeConstant.JCR_DATA) != null) {
              InputStream isP12 = jcrContent.getProperty(NodetypeConstant.JCR_DATA).getStream();
              videoCallModel.setP12Cert(isP12);
            }
          }
          if(videoCallNode.hasNode(VIDEO_PEM_CERT_NODE_NAME)) {
            Node pemCertNode = videoCallNode.getNode(VIDEO_PEM_CERT_NODE_NAME);
            Node jcrContent = pemCertNode.getNode(NodetypeConstant.JCR_CONTENT);
            if(jcrContent != null && jcrContent.getProperty(NodetypeConstant.JCR_DATA) != null) {
              InputStream isPem = jcrContent.getProperty(NodetypeConstant.JCR_DATA).getStream();
              videoCallModel.setPemCert(isPem);
            }
          }
          videoProfileCache.put(VIDEO_PROFILE_KEY, videoCallModel);
        }
      } catch (LoginException e) {
        if (LOG.isErrorEnabled()) {
          LOG.error("getWeemoKey() failed because of ", e.getMessage());
        }
      } catch (NoSuchWorkspaceException e) {
        if (LOG.isErrorEnabled()) {
          LOG.error("getWeemoKey() failed because of ", e.getMessage());
        }
      } catch (RepositoryException e) {
        if (LOG.isErrorEnabled()) {
          LOG.error("getWeemoKey() failed because of ", e.getMessage());
        }
      }
    }
    return videoCallModel;
  }
  ///////////////////////////////////////////////////////////////////////////////////////////  
  public String getWeemoKey() {
    String weemoKey = null;
    VideoCallModel videoCallModel = null;
    if(videoProfileCache != null && videoProfileCache.get(VIDEO_PROFILE_KEY) != null) {
      videoCallModel = videoProfileCache.get(VIDEO_PROFILE_KEY);      
    } else {
      videoCallModel = getVideoCallProfile();      
    }
    if(videoCallModel != null) {
      weemoKey = videoCallModel.getWeemoKey();
    }
    return weemoKey;
  }
  ///////////////////////////////////////////////////////////////////////////////////////////  
  public String getTokenKey() {
    String tokenKey = null;
    VideoCallModel videoCallModel = null;
    if(videoProfileCache != null && videoProfileCache.get(VIDEO_TOKEN_KEY) != null) {
      videoCallModel = videoProfileCache.get(VIDEO_TOKEN_KEY);      
     } else {
       videoCallModel = getVideoCallProfile();      
     }
    tokenKey = videoCallModel.getWeemoKey();
    return tokenKey;
  }
  
  /////////////////////////////////
  public boolean isDisableVideoCall() {
    boolean disableVideoCall = false;
    VideoCallModel videoCallModel = null;
    if(videoProfileCache != null && videoProfileCache.get(VIDEO_PROFILE_KEY) != null) {
      videoCallModel = videoProfileCache.get(VIDEO_PROFILE_KEY);      
    } else {
      videoCallModel = getVideoCallProfile();
    }
    if(videoCallModel.getDisableVideoCall() != null) {
      disableVideoCall = Boolean.valueOf(videoCallModel.getDisableVideoCall());
    }
    return disableVideoCall;
  }
  
  //////////////////////////////////////////////////////
  public void setDisableVideoCall(boolean disableVideoCall) {
    VideoCallModel videoCallModel = null;
    if(videoProfileCache != null && videoProfileCache.get(VIDEO_PROFILE_KEY) != null) {
      videoCallModel = videoProfileCache.get(VIDEO_PROFILE_KEY);      
    } else {
      videoCallModel = getVideoCallProfile();
    }
    videoCallModel.setDisableVideoCall(String.valueOf(disableVideoCall));
    saveVideoCallProfile(videoCallModel);   
  }
  //////////////////////////////////////////////////////////
  public boolean isExistVideoCallProfile() {
    boolean isExist = false;
    VideoCallModel videoCallModel = null;
    if(videoProfileCache != null && videoProfileCache.get(VIDEO_PROFILE_KEY) != null) {
      videoCallModel = videoProfileCache.get(VIDEO_PROFILE_KEY); 
      if(videoCallModel != null) isExist = true;  
    } else {
      SessionProvider sessionProvider = null;
      RepositoryService repositoryService = WCMCoreUtils.getService(RepositoryService.class);
      sessionProvider = WCMCoreUtils.getSystemSessionProvider();
      Session session;
      try {
        session = sessionProvider.getSession(WORKSPACE_NAME, repositoryService.getCurrentRepository());    
        Node rootNode = session.getRootNode();
        Node baseNode = rootNode.getNode(BASE_PATH);
        if(baseNode.hasNode(VIDEOCALL_BASE_PATH)) {
          isExist = true;
        }
      } catch (LoginException e) {
        if (LOG.isErrorEnabled()) {
          LOG.error("getWeemoKey() failed because of ", e);
        }
      } catch (NoSuchWorkspaceException e) {
        if (LOG.isErrorEnabled()) {
          LOG.error("getWeemoKey() failed because of ", e);
        }
      } catch (RepositoryException e) {
        if (LOG.isErrorEnabled()) {
          LOG.error("getWeemoKey() failed because of ", e);
        }
      } finally {
        if (sessionProvider != null) {
          sessionProvider.close();
        }
      }
    }     
    return isExist;
  }
  ////////////////////////////////////////////////////////
  public boolean isTurnOffVideoCall() throws Exception {
    boolean isTurnOff = true;
    VideoCallModel videoCallModel = null;
    if(videoProfileCache != null && videoProfileCache.get(VIDEO_PROFILE_KEY) != null) {
      videoCallModel = videoProfileCache.get(VIDEO_PROFILE_KEY);      
    } else {
      videoCallModel = getVideoCallProfile();
    }
    if(videoCallModel == null) return true;
    String str = videoCallModel.getDisableVideoCall();    
    if(Boolean.valueOf(str)) {
      return true; 
    } else {
      String videoCallsPermissions = videoCallModel.getVideoCallPermissions();
      if(StringUtils.isEmpty(videoCallsPermissions)) return true;
      
      String userId = ConversationState.getCurrent().getIdentity().getUserId();
      //Put list of permission into a map
      HashMap<String, String> permissionsMap = new HashMap<String, String>();
      String[] arrs = videoCallsPermissions.split(",");
      ArrayList<String> memberships = new ArrayList();
      for (String string : arrs) {
        if(string.split("#").length < 2) continue;
        String permission = string.split("#")[0];
        String value = string.split("#")[1];    
        permissionsMap.put(permission, value);
        if(permission.contains(":")) {
          memberships.add(permission);
        }
      }
      if(permissionsMap.get(userId) != null) {
        //Check permisson for user
        return !Boolean.valueOf(permissionsMap.get(userId));
      } else {
        //Check permission for membership
        UserACL userACL = WCMCoreUtils.getService(UserACL.class);
        for (String string : memberships) {
          if(userACL.hasPermission(string)) {
            boolean value = Boolean.valueOf(permissionsMap.get(string));
            if(value) return !value;
          }
        }       
      }
    }    
    return isTurnOff;
  }

}
