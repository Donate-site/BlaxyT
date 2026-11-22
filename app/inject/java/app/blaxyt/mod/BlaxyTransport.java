package app.blaxyt.mod;

import java.io.*;
import java.net.*;
import javax.net.ssl.*;

/** App-scoped HTTPS transport. The fallback preserves TLS hostname/chain validation. */
final class BlaxyTransport {
    private static final String HOST="doska67.duckdns.org", ADDRESS="31.77.16.230";
    private static final int LIMIT=1410000;
    private BlaxyTransport(){}
    static Net.Said talk(String address,String json)throws Exception{
        URL url=new URL(address);
        if(!"https".equals(url.getProtocol())||!HOST.equals(url.getHost())||url.getPort()!=-1||url.getUserInfo()!=null
                ||!(url.getPath().startsWith("/blaxyt/v1/voice/")||url.getPath().startsWith("/blaxyt/v1/link/")))throw new IOException("endpoint");
        byte[] body=json.getBytes("UTF-8");if(body.length>LIMIT)throw new IOException("limit");
        try{return ordinary(url,body);}
        catch(UnknownHostException|ConnectException|SocketTimeoutException e){return direct(url,body);}
    }
    private static Net.Said ordinary(URL url,byte[] body)throws Exception{
        HttpURLConnection link=(HttpURLConnection)url.openConnection();
        try{
            link.setInstanceFollowRedirects(false);link.setConnectTimeout(5000);link.setReadTimeout(25000);link.setRequestMethod("POST");link.setDoOutput(true);
            link.setRequestProperty("Content-Type","application/json; charset=utf-8");link.setRequestProperty("User-Agent","blaxyT/voice-2");link.setFixedLengthStreamingMode(body.length);
            try(OutputStream out=link.getOutputStream()){out.write(body);}
            int code=link.getResponseCode();InputStream input=code>=400?link.getErrorStream():link.getInputStream();
            if(input==null)return new Net.Said(code,"");
            try(InputStream in=input){return new Net.Said(code,new String(read(in,-1),"UTF-8"));}
        }finally{link.disconnect();}
    }
    private static Net.Said direct(URL url,byte[] body)throws Exception{
        // No global DNS/proxy changes and no certificate bypass. SNI and peer name stay HOST.
        try(Socket tcp=new Socket()){
            tcp.connect(new InetSocketAddress(ADDRESS,443),8000);tcp.setSoTimeout(25000);
            try(SSLSocket tls=(SSLSocket)((SSLSocketFactory)SSLSocketFactory.getDefault()).createSocket(tcp,HOST,443,true)){
                SSLParameters parameters=tls.getSSLParameters();boolean platformIdentity=true;
                try{parameters.setEndpointIdentificationAlgorithm("HTTPS");}catch(NoSuchMethodError oldAndroid){platformIdentity=false;}
                tls.setSSLParameters(parameters);tls.startHandshake();
                if(!platformIdentity&&!HttpsURLConnection.getDefaultHostnameVerifier().verify(HOST,tls.getSession()))throw new SSLHandshakeException("HTTPS peer name mismatch");
                OutputStream out=tls.getOutputStream();
                String header="POST "+url.getFile()+" HTTP/1.1\r\nHost: "+HOST+"\r\nContent-Type: application/json; charset=utf-8\r\nUser-Agent: blaxyT/voice-2\r\nAccept-Encoding: identity\r\nConnection: close\r\nContent-Length: "+body.length+"\r\n\r\n";
                out.write(header.getBytes("US-ASCII"));out.write(body);out.flush();
                BufferedInputStream in=new BufferedInputStream(tls.getInputStream());
                String[] status=line(in).split(" ",3);if(status.length<2||!status[0].startsWith("HTTP/1."))throw new IOException("HTTP status");
                int code=Integer.parseInt(status[1]),length=-1,headers=0;boolean chunks=false;
                for(String h;(h=line(in)).length()!=0;){if((headers+=h.length())>16384)throw new IOException("headers");int at=h.indexOf(':');if(at<1)throw new IOException("header");String key=h.substring(0,at),value=h.substring(at+1).trim();if(key.equalsIgnoreCase("Content-Length")){length=Integer.parseInt(value);if(length<0||length>LIMIT)throw new IOException("limit");}if(key.equalsIgnoreCase("Transfer-Encoding")){if(!value.equalsIgnoreCase("chunked"))throw new IOException("encoding");chunks=true;}}
                ByteArrayOutputStream result=new ByteArrayOutputStream();
                if(chunks){for(;;){String size=line(in).split(";",2)[0];int n=Integer.parseInt(size,16);if(n<0||n>LIMIT-result.size())throw new IOException("limit");if(n==0)break;result.write(read(in,n));if(!line(in).isEmpty())throw new IOException("chunk");}}
                else result.write(read(in,length));
                return new Net.Said(code,new String(result.toByteArray(),"UTF-8"));
            }
        }
    }
    private static String line(InputStream in)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();int b;
        while((b=in.read())!=-1){if(b=='\n'){byte[] value=out.toByteArray();if(value.length==0||value[value.length-1]!='\r')throw new IOException("line");return new String(value,0,value.length-1,"US-ASCII");}if(out.size()>=8192)throw new IOException("line");out.write(b);}throw new EOFException();
    }
    private static byte[] read(InputStream in,int length)throws IOException{
        if(length>LIMIT)throw new IOException("limit");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] block=new byte[8192];
        while(length<0||out.size()<length){int n=in.read(block,0,length<0?block.length:Math.min(block.length,length-out.size()));if(n<0){if(length>=0)throw new EOFException();break;}if(out.size()+n>LIMIT)throw new IOException("limit");out.write(block,0,n);}return out.toByteArray();
    }
}
