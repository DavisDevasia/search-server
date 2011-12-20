package org.musicbrainz.search.servlet;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.rmi.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class RateLimiterChecker {

    private static final Logger log = Logger.getLogger(RateLimiterChecker.class.getName());


    private static String HEADER_REQUEST_ADDRESS="X-MB-Remote-Addr";
    private static String HEADER_APPLY_RATE_LIMIT="X-Apply-Rate-Limit";
    public  static String HEADER_RATE_LIMITED="X-Rate-Limited";
    private static String MSG_SERVER_BUSY_SIMPLE
                = "The MusicBrainz search server is currently busy. Please try again later.";
    private static String MSG_SERVER_BUSY
            = "The MusicBrainz search server is currently busy. Please try again later." +
              "%nYou make %s requests per %s seconds, but you're currently making %s requests";

    private static String MSG_HEADER
            = "%s %s %s";
    private static Pattern pe;
    private static InetAddress  rateLimiterHost;
    private static Integer      rateLimiterPort;
    private static boolean      rateLimiterConfigured =false;
    private static final String OVER_LIMIT_SEARCH_IP = "over_limit search ip=";
    private static final RateLimiterResponse ALWAYS_TRUE = new RateLimiterResponse();

    public static void init(String host, String port)
    {
        try {
            pe = Pattern.compile("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$");
        }
        catch(PatternSyntaxException pe) {
                    log.log(Level.SEVERE, "Unable to compile pattern:"+pe.getMessage(),pe);
            return;
        }

        try {
            rateLimiterHost=InetAddress.getByName(host);
        }
        catch(java.net.UnknownHostException uhe) {
            log.log(Level.SEVERE, "Unable to init rate limiter:"+uhe.getMessage(),uhe);
            return;
        }

        try {
            rateLimiterPort=Integer.parseInt(port);
        }
        catch(NumberFormatException ne) {
            log.log(Level.SEVERE, "Unable to init rate limiter:"+ne.getMessage(),pe);
            return;
        }

        rateLimiterConfigured =true;
    }

    /**
     * Is it a valid dot-quad IP address
     *
     * @param ipaddress
     * @return
     */
    private static boolean isValidIpAddress(String ipaddress)
    {
        return pe.matcher(ipaddress).matches();
    }

    /**
     * Is Search setup to use a Rate Lmiter
     *
     * @return
     */
    private static boolean isRateLimiterConfigured()
    {
        return rateLimiterConfigured;
    }

    /**
     * Call Rate Limiter to see if query is allowed
     *
     * @param remoteIpAddress
     * @return
     */
    private static RateLimiterResponse validateAgainstRateLimiter(String remoteIpAddress)
    {
        try {

            //Send Request
            String rateLimiter=OVER_LIMIT_SEARCH_IP+remoteIpAddress;
            byte[] msg = rateLimiter.getBytes();
            DatagramSocket ds = new DatagramSocket();
            DatagramPacket dp = new DatagramPacket(msg,msg.length,rateLimiterHost,rateLimiterPort.intValue());
            ds.send(dp);

            //Get Response
            byte[] receiveData = new byte[30];
            DatagramPacket dpReceive = new DatagramPacket(receiveData, receiveData.length);
            ds.receive(dpReceive);

            //Parse Response
            String result = new String(dpReceive.getData());

            //Response is in format ok %s %.1f %.1f %d
            RateLimiterResponse rlr = new RateLimiterResponse(result);
            return rlr;

        }
        catch(UnknownHostException uhe) {
            log.log(Level.SEVERE, "ValidateAgainstRateLimiter:"+uhe.getMessage(),uhe);
        }
        catch(SocketException se) {
            log.log(Level.SEVERE, "ValidateAgainstRateLimiter:"+se.getMessage(),se);

        }
        catch(IOException ioe) {
            log.log(Level.SEVERE, "ValidateAgainstRateLimiter:"+ioe.getMessage(),ioe);
        }
        return ALWAYS_TRUE;
    }

    /**
     *
     * @param request
     * @return
     */
    public static RateLimiterResponse checkRateLimiter(HttpServletRequest request)
    {
        if(!isRateLimiterConfigured())
        {
            return ALWAYS_TRUE;
        }

        String applyRateLimiter=request.getHeader(HEADER_APPLY_RATE_LIMIT);

        if((applyRateLimiter==null) || (applyRateLimiter.length()==0) || (!applyRateLimiter.equals("yes")))
        {
            return ALWAYS_TRUE;
        }

        String remoteIpAddress=request.getHeader(HEADER_REQUEST_ADDRESS);
        if((remoteIpAddress==null) || (remoteIpAddress.length()==0) ||(!isValidIpAddress(remoteIpAddress)))
        {
            return ALWAYS_TRUE;
        }
        return validateAgainstRateLimiter(remoteIpAddress);
    }

    /**
     * Wraps the rate limiter response
     */
    static class RateLimiterResponse
    {
        private boolean valid       = false;
        private String  msg         = null;
        private String  headerMsg   = "";
        private String  rate        = "";
        private String  limit       = "";
        private String  period      = "";


        //Dummy constructor for true;
        RateLimiterResponse()
        {
            valid = true;
        }

        RateLimiterResponse(String  response)
        {
            if(response.startsWith("ok y"))
            {
                valid = true;
            }
            else
            {
                String[] parts = response.substring(5).split(" ");
                if(parts.length>=3) {
                    rate=parts[0];
                    limit=parts[1];
                    period=parts[2];
                    msg=String.format(MSG_SERVER_BUSY, limit, period, rate);
                    headerMsg=String.format(MSG_HEADER, rate, limit, period );
                }
                else {
                    msg=String.format(MSG_SERVER_BUSY_SIMPLE);
                }

            }
        }

        public boolean isValid()
        {
            return valid;
        }

        public String getMsg()
        {
            return msg;
        }

        public String getHeaderMsg()
        {
            return msg;
        }


        public String getRate() {
            return rate;
        }

        public String getLimit() {
            return limit;
        }

        public String getPeriod() {
            return period;
        }
    }
}
