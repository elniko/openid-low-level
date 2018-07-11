package com.example.testoicd;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.ssl.SSLContexts;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@RestController
@RequestMapping( "/" )
public class TestController {

    private static final String OPENID_SERVER_BASE_URL = "https://localhost:9443";
    private static final String LOCAL_SERVER_BASE_URL = "https://localhost:8081";

    private static final String CLIENT_KEY = "9ThSY82u8gIVmPlVJ075tL1J0fka";
    private static final String CLIENT_SECRET = "1otE7VkXQcANC_GZ1vFMFpMqqlYa";

    @RequestMapping( value = "/start", method = RequestMethod.GET, produces = MediaType.TEXT_PLAIN_VALUE )
    public void start( HttpServletResponse response ) {
        String url = OPENID_SERVER_BASE_URL + "/oauth2/authorize?response_type=code&scope=openid&client_id=" + CLIENT_KEY + "&redirect_uri=" + LOCAL_SERVER_BASE_URL + "/connect";
        System.out.println( "start, redirectin to " + url );
        try {
            response.sendRedirect( url );
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @RequestMapping( value = "/connect", method = RequestMethod.GET, produces = MediaType.TEXT_PLAIN_VALUE )
    public String connect(@RequestParam String code, @RequestParam( required = false ) String session_state ,
                          HttpServletResponse servletReponse ) {
        try {
            System.out.println( "Got code : " + code  + " / session_state = " + session_state );

            String url = OPENID_SERVER_BASE_URL + "/oauth2/token";

            CloseableHttpClient client =
                    HttpClients.custom()
                            .setSSLSocketFactory(new SSLConnectionSocketFactory(SSLContexts.custom()
                                            .loadTrustMaterial(null, new TrustSelfSignedStrategy())
                                            .build()
                                    )
                            ).build();

            HttpPost post = new HttpPost( url );

            List<NameValuePair> params = new ArrayList<>();
            params.add( new BasicNameValuePair( "client_id", CLIENT_KEY ) );
            params.add( new BasicNameValuePair( "grant_type", "authorization_code" ) );
            params.add( new BasicNameValuePair( "code", code ) );
            params.add( new BasicNameValuePair( "redirect_uri", LOCAL_SERVER_BASE_URL + "/connect" ) );
            post.setEntity( new UrlEncodedFormEntity( params ));

            UsernamePasswordCredentials credentials = new UsernamePasswordCredentials( CLIENT_KEY, CLIENT_SECRET );
            post.addHeader( new BasicScheme().authenticate( credentials, post, null ));

            CloseableHttpResponse response = client.execute( post );

            InputStream is = response.getEntity().getContent();
            StringWriter writer = new StringWriter();
            IOUtils.copy( is , writer, "UTF-8" );
            String tokenAsString = writer.toString();
            System.out.println( "Got token : " + tokenAsString );
            client.close();

            ObjectMapper mapper = new ObjectMapper();
            Map<String,Object> tokenAsMap = mapper.readValue( tokenAsString, new TypeReference<Map<String,Object>>(){});
            dumpJsonAsMap( tokenAsMap, "Token" );

            String id_tokenAsJWT = (String) tokenAsMap.get( "id_token" );
            String[] id_tokenParts = id_tokenAsJWT.split( "\\." );
            String JOSE_header = new String( Base64.getDecoder().decode( id_tokenParts[ 0 ] ) );
            System.out.println( "JOSE header : " + JOSE_header );

            Map<String,Object> JOSEHeaderAsMap = mapper.readValue( JOSE_header, new TypeReference<Map<String,Object>>(){});
            dumpJsonAsMap( JOSEHeaderAsMap, "JWTHeader" );


            String JWT_payload = new String( Base64.getDecoder().decode( id_tokenParts[ 1 ] ) );
            System.out.println( "JWT payload : " + JWT_payload );
            Map<String,Object> JWTPayloadMap = mapper.readValue( JWT_payload, new TypeReference<Map<String,Object>>(){});
            dumpJsonAsMap( JWTPayloadMap, "JWTPayload" );

            String JWT_signature = ( id_tokenParts.length == 2 ) ? null : id_tokenParts[ 2 ];



        } catch (AuthenticationException e) {
            e.printStackTrace();
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (KeyStoreException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }


        return "connect with code : " + code;
    }


    private void dumpJsonAsMap( Map<String,Object> jsonAsMap, String prefix ) {
        for ( Map.Entry<String,Object> entry : jsonAsMap.entrySet() ) {
            System.out.print( prefix + "/" + entry.getKey() + " = " );
            Object value = entry.getValue();
            if ( value instanceof  String   ||  value instanceof  Integer ) {
                System.out.print( value );
            }
            else {
                System.out.print( "###COMPLEX VALUE" );
            }
            System.out.println();
        }
    }
}
