import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

public class curl_code {

    public static void main(String[] args) throws IOException, InterruptedException{
        JSONObject n2data = new JSONObject("{\"n2Information\":{\"n2InformationClass\":\"PWS\",\"pwsInfo\":{\"serialNumber\":32752,\"messageIdentifier\":\"4380\",\"pwsContainer\":{\"ngapData\":{\"contentId\":\"pwsN2InformationContent\"}}}}}");
        JSONObject wrrep = new JSONObject();
        String ins = "";
        POST_request_to_AMF(n2data, wrrep, ins);
    }

    private static void POST_request_to_AMF(JSONObject n2info_data, JSONObject wr_rep_data, String inst_id) throws IOException, InterruptedException {
        
        String tnsfr_amf_url = "http://[2405:0200:1410:1412:0000:0000:0018:72]:10107" + "/namf-comm/v1/non-ue-n2-messages/transfer";
        
        List<String> headers = List.of(
            "Content-Type: application/json",
            "Content-Type: application/vnd.3gpp.ngap",
            "Content-Id: pwsN2InformationContent"
        );

        String boundary = "its_boundary_97m97n.115";   // 23 char (max 70 chars allowed)
        String crlf = "\r\n";
        String separator = "--" + boundary + crlf;
        String trailer = crlf + "--" + boundary + "--" + crlf;

        String ngap_hex_data = "003300808800000700230002111C005F00027FF000570002003C002F00020001007A400A400000000485260000010014400101007B405600530154747A0E4ACF416110BD3CA783C2ECB29CCE022DD36E323B0F4A9FDD6F79D9D568341A8D46A3D168341A8D46A3D168341A8D46A3D168341A8D46A3D168341A8D46A3D168341A8D46A3D168341A8D46A3D10020";
        //String ngap_hex_data = NgapEncoder.encode_to_ngap(wr_rep_data.toString());


        String multipartData = separator + headers.get(0) + crlf + crlf + n2info_data.toString() + crlf + separator 
                                + headers.get(1) + crlf + headers.get(2) + crlf + crlf + ngap_hex_data + trailer;

        List<String> command = new ArrayList<>();
        command.add("curl");
        command.add("-s");
        command.add("-i");
        command.add("-X");
        command.add("POST");
        command.add("--http2-prior-knowledge");
        command.add("-H");
        command.add("accept: application/json");
        command.add("-H");
        command.add("Content-Type: multipart/related; boundary=" + boundary);
        command.add("-H");
        command.add("User-Agent: CBCF");
        command.add("-d");
        command.add(multipartData);
        command.add(tnsfr_amf_url);
        
        execute_curl(command);
    }

    private static String execute_curl(List<String> command) throws IOException, InterruptedException {

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
        System.out.println("Exit Code: " + exitCode);
        System.out.println("Curl Output:\n" + output.toString());

        return output.toString();
    }
}
