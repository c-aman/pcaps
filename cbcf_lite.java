import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class cbcf_lite {
    
    
    public static JSONObject data = get_all_data("cbcf_lite_info.json");

    public static String nf_id = data.getString("cbcf_nf_id");
    public static String scpc_url = data.getString("scpC");
    public static String scpp_url = data.getString("scpP");
    public static String system_ipv6 = data.getString("sys_ipv6");
    public static String http2_flag = "--http2-prior-knowledge";
    public static String sys_interface = data.getString("sys_interface");;
    public static long HB_time = data.getInt("hb_time");;  // heart-beat time in seconds
    public static ScheduledExecutorService schedulerHB;
    
    public static String callback = data.getString("callback");
    public static String usr = "";
    public static String pwd = "";
    public static String amf_ip = data.getString("amfip");
    public static String amf_port = data.getString("amfport");
    public static String amf_inst_id = data.getString("amf_inst_id");
    public static JSONArray plmn_arr = data.getJSONArray("target_amf_plmn");
    public static JSONArray plmn_cbcf = data.getJSONArray("cbcf_plmn");
    
    public static void main(String[] args) throws Exception{

        int choice = 10000;
        String choices = "";
        choices += "\n0. Exit utility ";
        choices += "\n1. Register CBCF with NRF & intiate heartbeat";
        choices += "\n2. Unregister CBCF from NRF ";
        choices += "\n3. Send a PATCH to NRF ";
        choices += "\n4. Discover AMF based on PLMN ";
        choices += "\n5. Send NFStatus Subscribe to NRF ";
        choices += "\n6. Send N2Subscribe to AMF via SCP-P";
        choices += "\n7. Send wr-rep to AMF via SCP-P ";
        choices += "\n8. Send N2Subscribe directly to AMF IP ";
        choices += "\n9. Send wr-rep directly to AMF IP ";
        System.out.println("\n********************\n" + choices);

        while(choice > 0){
             Scanner myObj = new Scanner(System.in);
                System.out.print("\nEnter choice: ");
                choice = myObj.nextInt();
            
            if (choice == 0){
                System.out.print("Doing Unsubsribe & Exiting..");
                deregister();
                break;
            }

            if (choice == 1){
                register();
            }
            else if (choice == 2){
                deregister();
            }
            else if (choice == 3){
                send_HB_NRF();
            }
            else if (choice == 4){
                get_AMF_details();
            }
            else if (choice == 5){
                send_NFStatusSub();
            }
            else if (choice == 6){
                // send_N2Subscribe(scpp_url);
                send_N2Subscribe(scpp_url);
            }
            else if (choice == 7){
                send_wr_rep();
            }
            else if (choice == 8){
                direct_N2Subscribe();
            }
            else if (choice == 9){
                direct_send_wr_rep();
            }
            else{
                System.out.println("No Valid Choice !!\n");
            }

        }
    }

    public static JSONObject get_all_data(String path){
        JSONObject data = new JSONObject();
        try {
            data = new JSONObject(new String(Files.readAllBytes(Paths.get(path))));
        } catch (JSONException | IOException e) {
            e.printStackTrace();
        }
        return data;
    }
    
    public static void register() throws Exception {

        PUT_request_to_NRF();
        schedulerHB = Executors.newScheduledThreadPool(1);
        schedulerHB.scheduleAtFixedRate(new Runnable() {
           @Override
           public void run() {
                try {
                    send_HB_NRF();
                } 
                catch (IOException | InterruptedException e) {
                    System.out.println("Got an exception while sending heartbeat to NRF - " + e);
                }
           }
        }, 0, HB_time, TimeUnit.SECONDS);
    }
    
    
    public static void deregister() throws Exception {
        try {
            schedulerHB.shutdownNow();
            DELETE_request_NRF();
        }
        catch (Exception e){
            System.out.println("Got an exception while de-registering CBCF " + e);
        }
        
    }


    private static void PUT_request_to_NRF() throws IOException, InterruptedException {

        JSONObject nrf_json = new JSONObject();
        nrf_json.put("nfInstanceId", nf_id);
        nrf_json.put("nfType", "CBCF");      
        nrf_json.put("nfStatus", "REGISTERED");
        JSONArray ip6_arr = new JSONArray();
        ip6_arr.put(system_ipv6);
        nrf_json.put("ipv6Addresses", ip6_arr); 

        JSONArray cbcf_plmn = plmn_cbcf;
        nrf_json.put("plmnList", cbcf_plmn);


        String scp_reg_url = scpc_url + "/nnrf-nfm/v1/nf-instances/" + nf_id;
        List<String> command = new ArrayList<>();
        command.add("curl");
        command.add("--interface");
        command.add(sys_interface);
        command.add("-s");
        command.add("-i");
        command.add("-X");
        command.add("PUT");
        command.add(http2_flag);
        command.add("-H");
        command.add("accept: application/json");
        command.add("-H");
        command.add("Content-Encoding: application/json");
        command.add("-H");
        command.add("Accept-Encoding: application/json");
        command.add("-H");
        command.add("Content-Type: application/json");
        command.add("-H");
        command.add("User-Agent: CBCF");
        command.add("-d");
        command.add(nrf_json.toString());
        command.add(scp_reg_url);

        String[] res = execute_curl(command);
        try {
            String[] arr = res[3].split("\n");
            usr = arr[3].split(":")[1];
            pwd = arr[4].split(":")[1];
        }
        catch (Exception e){
            System.out.println("Exception while getting username/pwd while registration " + e);
        }
        

        String output = String.join("", res);
        System.out.println(output);
    }

    private static void DELETE_request_NRF() throws IOException, InterruptedException {
        
        String scp_del_url = scpc_url + "/nnrf-nfm/v1/nf-instances/" + nf_id;
        
        List<String> command = new ArrayList<>();
        command.add("curl");
        command.add("--interface");
        command.add(sys_interface);
        command.add("-s");
        command.add("-i");
        command.add("-X");
        command.add("DELETE");
        command.add(http2_flag);
        command.add("-H");
        command.add("accept: */*");
        command.add("-H");
        command.add("User-Agent: CBCF");
        command.add(scp_del_url);

        String[] res = execute_curl(command);
        String output = String.join("", res);
        System.out.println(output);
    }


    private static String send_HB_NRF() throws IOException, InterruptedException {

        String patch_data = data.getJSONArray("patch_data_HB").toString();

        String scp_reg_url = scpc_url + "/nnrf-nfm/v1/nf-instances/" + nf_id;
        
        List<String> command = new ArrayList<>();
        command.add("curl");
        command.add("--interface");
        command.add(sys_interface);
        command.add("-s");
        command.add("-i");
        command.add("-X");
        command.add("PATCH");
        command.add(http2_flag);
        command.add("-H");
        command.add("Content-Type: application/json-patch+json");
        command.add("-H");
        command.add("User-Agent: CBCF");
        command.add("-d");
        command.add(patch_data);
        command.add(scp_reg_url);

        String[] arr = execute_curl(command);
        String code = "Note Sent";
        try {
            code = (arr[3].split(" ")[0] + " " + arr[3].split(" ")[1]);
        }
        catch (Exception e){
            System.out.println("HB will not be sent.");
        }
        
        System.out.println("\n>>>> Sent HEARTBEAT to NRF from CBCF: " + code + "\n");
        return code;     
    }

    public static void get_AMF_details() throws IOException, InterruptedException{
        String scp_get_url = scpc_url + "/nnrf-disc/v1/nf-instances?target-nf-type=AMF&requester-nf-type=CBCF&target-plmn-list="
                            + plmn_arr.toString(); 
        
        List<String> command = new ArrayList<>();
        command.add("curl");
        command.add("--interface");
        command.add(sys_interface);
        command.add("-s");
        command.add("-g");
        command.add("-X");
        command.add("GET");
        command.add(http2_flag);
        command.add("-H");
        command.add("accept: application/json");
        command.add("-H");
        command.add("Accept-Encoding: application/json");
        command.add("-H");
        command.add("User-Agent: CBCF");
        command.add(scp_get_url);
        
        String[] res = execute_curl(command);
        String output = String.join("", res);
        System.out.println(output);

        try {
            JSONObject amf_data = new JSONObject(res[3]);
            JSONArray nf_arr = amf_data.getJSONArray("nfInstances");
            JSONObject amf1 = (JSONObject) nf_arr.get(0);
            amf_inst_id = amf1.getString("nfInstanceId"); 
            amf_ip = (String) amf1.getJSONArray("ipv6Addresses").get(0);
        } catch (Exception e){
            System.out.println("Got exception while parsing AMF details response frmo AMF." + e + "\nNow using default values.");
        }
        
    }

    public static void send_NFStatusSub() throws IOException, InterruptedException{

        JSONObject reg_json = new JSONObject();
        reg_json.put("nfStatusNotificationUri", callback);
        reg_json.put("reqNfInstanceId", nf_id);
        reg_json.put("reqNfType", "CBCF");

        JSONObject plmn = new JSONObject(plmn_arr.get(0).toString());
        JSONObject cond = new JSONObject("{\"nfInstanceId\": " + amf_inst_id + "}");
        reg_json.put("plmnId", plmn);
        reg_json.put("subscrCond", cond);

        String scp_reg_url = scpc_url + "/nnrf-nfm/v1/subscriptions";
        List<String> command = new ArrayList<>();
        command.add("curl");
        command.add("--interface");
        command.add(sys_interface);
        command.add("-s");
        command.add("-i");
        command.add("-X");
        command.add("POST");
        command.add(http2_flag);
        command.add("-H");
        command.add("accept: application/json");
        command.add("-H");
        command.add("Content-Encoding: application/json");
        command.add("-H");
        command.add("Accept-Encoding: application/json");
        command.add("-H");
        command.add("Content-Type: application/json");
        command.add("-H");
        command.add("User-Agent: CBCF");
        command.add("-d");
        command.add(reg_json.toString());
        command.add(scp_reg_url);

        String[] res = execute_curl(command);
        String output = String.join("", res);
        System.out.println(output);
    }


    public static void send_N2Subscribe(String url) throws IOException, InterruptedException{
        String subs_amf_url = url + "/namf-comm/v1/non-ue-n2-messages/subscriptions";

        JSONObject subscribe_data = new JSONObject();
        subscribe_data.put("n2InformationClass", "PWS");          
        subscribe_data.put("n2NotifyCallbackUri", URI.create(callback));      // IE 4

        String data = subscribe_data.toString();

        List<String> command = new ArrayList<>();
        command.add("curl");
        command.add("--interface");
        command.add(sys_interface);
        command.add("-s");
        command.add("-i");
        command.add("-X");
        command.add("POST");
        command.add(http2_flag);
        command.add("-H");
        command.add("3gpp-sbi-Discovery-target-nf-type: AMF");
        command.add("-H");
        command.add("3gpp-sbi-Discovery-requester-nf-type: CBCF");
        command.add("-H");
        command.add("3gpp-sbi-Discovery-service-names: namf-comm");
        command.add("-H");
        command.add("3gpp-Sbi-Discovery-target-plmn-list: " + plmn_arr.toString());
        command.add("-H");
        command.add("3gpp-sbi-routing-binding: bl=nf-instance; nfinst=" + amf_inst_id);
        command.add("-H");
        command.add("accept: application/json");
        command.add("-H");
        command.add("Content-Type: application/json");
        command.add("-H");
        command.add("User-Agent: CBCF");
        command.add("-d");
        command.add(data);
        command.add(subs_amf_url);
        
        String[] res = execute_curl(command);
        String output = String.join("", res);
        System.out.println(output);
    }


    public static void send_wr_rep() throws IOException, InterruptedException{

        String tnsfr_amf_url = scpp_url + "/namf-comm/v1/non-ue-n2-messages/transfer";
        JSONObject n2data = data.getJSONObject("n2Data");
        //JSONObject n2data = new JSONObject("{\"n2Information\":{\"n2InformationClass\":\"PWS\",\"pwsInfo\":{\"serialNumber\":32752,\"sendRanResponse\": true,\"messageIdentifier\":\"4380\",\"pwsContainer\":{\"ngapData\":{\"contentId\":\"pwsN2InformationContent\"}}}}}");
        
        List<String> headers = List.of(
            "Content-Type: application/json",
            "Content-Type: application/vnd.3gpp.ngap",
            "Content-Id: pwsN2InformationContent"
        );

        String boundary = "its_boundary123";   // 23 char (max 70 chars allowed)
        String crlf = "\r\n";
        String separator = "--" + boundary + crlf;
        String trailer = crlf + "--" + boundary + "--" + crlf;

        String ngap_hex_data = data.getString("ngapData");
        //"003300808800000700230002111C005F00027FF000570002003C002F00020001007A400A400000000485260000010014400101007B405600530154747A0E4ACF416110BD3CA783C2ECB29CCE022DD36E323B0F4A9FDD6F79D9D568341A8D46A3D168341A8D46A3D168341A8D46A3D168341A8D46A3D168341A8D46A3D168341A8D46A3D168341A8D46A3D10020";
        int len = ngap_hex_data.length();
        byte[] ngap_bytes = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int high = Character.digit(ngap_hex_data.charAt(i), 16);
            int low = Character.digit(ngap_hex_data.charAt(i + 1), 16);
            ngap_bytes[i / 2] = (byte) ((high << 4) + low);
        }
        
        // String multipartData = separator + headers.get(0) + crlf + crlf + n2data.toString() + crlf + separator 
        //                         + headers.get(1) + crlf + headers.get(2) + crlf + crlf + ngap_hex_data + trailer;

        byte[] multipart_data = concat_byte_arrays(separator.getBytes(), headers.get(0).getBytes(), crlf.getBytes(), crlf.getBytes(), n2data.toString().getBytes(), crlf.getBytes(), separator.getBytes() 
                , headers.get(1).getBytes(), crlf.getBytes(), headers.get(2).getBytes(), crlf.getBytes(), crlf.getBytes(), ngap_bytes, trailer.getBytes());


        List<String> command = new ArrayList<>();
        command.add("curl");
        command.add("--interface");
        command.add(sys_interface);
        command.add("-s");
        command.add("-i");
        command.add("-X");
        command.add("POST");
        command.add(http2_flag);
        command.add("-H");
        command.add("3gpp-sbi-Discovery-target-nf-type: AMF");
        command.add("-H");
        command.add("3gpp-sbi-Discovery-requester-nf-type: CBCF");
        command.add("-H");
        command.add("3gpp-sbi-Discovery-service-names: namf-comm");
        command.add("-H");
        command.add("3gpp-Sbi-Discovery-target-plmn-list: " + plmn_arr.toString());
        command.add("-H");
        command.add("3gpp-sbi-routing-binding: bl=nf-instance; nfinst=" + amf_inst_id);
        command.add("-H");
        command.add("accept: application/json");
        command.add("-H");
        command.add("Content-Type: multipart/related; boundary=" + boundary);
        command.add("-H");
        command.add("User-Agent: CBCF");
        command.add("--data-binary");
        command.add("@-");
        command.add(tnsfr_amf_url);
        
        String[] res = execute_curl_bytes(command, multipart_data);
        String output = String.join("", res);
        System.out.println(output);
    }

    public static void direct_N2Subscribe() throws IOException, InterruptedException{
        String subs_amf_url = "http://[" + amf_ip + "]:" + amf_port 
                            + "/namf-comm/v1/non-ue-n2-messages/subscriptions";

        JSONObject subscribe_data = new JSONObject();
        subscribe_data.put("n2InformationClass", "PWS");          
        subscribe_data.put("n2NotifyCallbackUri", URI.create(callback));      // IE 4

        String data = subscribe_data.toString();

        List<String> command = new ArrayList<>();
        command.add("curl");
        command.add("--interface");
        command.add(sys_interface);
        command.add("-s");
        command.add("-i");
        command.add("-X");
        command.add("POST");
        command.add(http2_flag);
        command.add("-H");
        command.add("accept: application/json");
        command.add("-H");
        command.add("Content-Type: application/json");
        command.add("-H");
        command.add("User-Agent: CBCF");
        command.add("-d");
        command.add(data);
        command.add(subs_amf_url);
        
        String[] res = execute_curl(command);
        String output = String.join("", res);
        System.out.println(output);
    }

    public static void direct_send_wr_rep() throws IOException, InterruptedException{

        String tnsfr_amf_url = "http://[" + amf_ip + "]:" + amf_port 
                             + "/namf-comm/v1/non-ue-n2-messages/transfer";

        JSONObject n2data = data.getJSONObject("n2Data");
        //JSONObject n2data = new JSONObject("{\"n2Information\":{\"n2InformationClass\":\"PWS\",\"pwsInfo\":{\"serialNumber\":32752,\"messageIdentifier\":\"4380\",\"pwsContainer\":{\"ngapData\":{\"contentId\":\"pwsN2InformationContent\"}}}}}");
        
        List<String> headers = List.of(
            "Content-Type: application/json",
            "Content-Type: application/vnd.3gpp.ngap",
            "Content-Id: pwsN2InformationContent"
        );

        String boundary = "its_boundary123";   // 23 char (max 70 chars allowed)
        String crlf = "\r\n";
        String separator = "--" + boundary + crlf;
        String trailer = crlf + "--" + boundary + "--" + crlf;

        String ngap_hex_data = data.getString("ngapData");
        //"003300808800000700230002111C005F00027FF000570002003C002F00020001007A400A400000000485260000010014400101007B405600530154747A0E4ACF416110BD3CA783C2ECB29CCE022DD36E323B0F4A9FDD6F79D9D568341A8D46A3D168341A8D46A3D168341A8D46A3D168341A8D46A3D168341A8D46A3D168341A8D46A3D168341A8D46A3D10020";
        int len = ngap_hex_data.length();
        byte[] ngap_bytes = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int high = Character.digit(ngap_hex_data.charAt(i), 16);
            int low = Character.digit(ngap_hex_data.charAt(i + 1), 16);
            ngap_bytes[i / 2] = (byte) ((high << 4) + low);
        }
        
        // String multipartData = separator + headers.get(0) + crlf + crlf + n2data.toString() + crlf + separator 
        //                         + headers.get(1) + crlf + headers.get(2) + crlf + crlf + ngap_hex_data + trailer;

        byte[] multipart_data = concat_byte_arrays(separator.getBytes(), headers.get(0).getBytes(), crlf.getBytes(), crlf.getBytes(), n2data.toString().getBytes(), crlf.getBytes(), separator.getBytes() 
                , headers.get(1).getBytes(), crlf.getBytes(), headers.get(2).getBytes(), crlf.getBytes(), crlf.getBytes(), ngap_bytes, trailer.getBytes());

        List<String> command = new ArrayList<>();
        command.add("curl");
        command.add("--interface");
        command.add(sys_interface);
        command.add("-s");
        command.add("-i");
        command.add("-X");
        command.add("POST");
        command.add(http2_flag);
        command.add("-H");
        command.add("accept: application/json");
        command.add("-H");
        command.add("Content-Type: multipart/related; boundary=" + boundary);
        command.add("-H");
        command.add("User-Agent: CBCF");
        command.add("--data-binary");
        command.add("@-");
        command.add(tnsfr_amf_url);
        
        String[] res = execute_curl_bytes(command, multipart_data);
        String output = String.join("", res);
        System.out.println(output);
    }


    private static String[] execute_curl(List<String> command) throws IOException, InterruptedException {

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder output = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }

        int exitCode = process.waitFor();
        String[] res_arr = new String[4];
        res_arr[0] = "Exit code: ";
        res_arr[1] = Integer.toString(exitCode);
        res_arr[2] = "\nOutput: ";
        res_arr[3] = output.toString();

        return res_arr;
    }


    private static String[] execute_curl_bytes(List<String> command, byte[] binary_data) throws IOException, InterruptedException {

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();

        try (OutputStream outputS = process.getOutputStream()){
        	outputS.write(binary_data);
            outputS.close();
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder output = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }

        int exitCode = process.waitFor();
        String[] res_arr = new String[4];
        res_arr[0] = "Exit code: ";
        res_arr[1] = Integer.toString(exitCode);
        res_arr[2] = "\nOutput: ";
        res_arr[3] = output.toString();

        return res_arr;
    }


    public static byte[] concat_byte_arrays(byte[]...arrays) {
        int totalLength = 0;
        for (int i = 0; i < arrays.length; i++){
            totalLength += arrays[i].length;
        }

        byte[] result = new byte[totalLength];
        int currentIndex = 0;
        for (int i = 0; i < arrays.length; i++){
            System.arraycopy(arrays[i], 0, result, currentIndex, arrays[i].length);
            currentIndex += arrays[i].length;
        }

        return result;
    }
    
}
