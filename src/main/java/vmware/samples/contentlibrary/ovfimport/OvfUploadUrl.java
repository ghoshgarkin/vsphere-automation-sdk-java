package vmware.samples.contentlibrary.ovfimport;

import com.vmware.content.LibraryTypes;
import com.vmware.content.library.ItemModel;
import com.vmware.content.library.item.TransferEndpoint;
import com.vmware.content.library.item.UpdateSession;
import com.vmware.content.library.item.UpdateSessionModel;
import com.vmware.content.library.item.updatesession.FileTypes;
import org.apache.commons.cli.Option;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.utils.IOUtils;
import vmware.samples.common.SamplesAbstractBase;
import vmware.samples.contentlibrary.client.ClsApiClient;
import com.vmware.content.library.item.updatesession.File;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Created by ghoshg on 13/05/21
 */
public class OvfUploadUrl extends SamplesAbstractBase {

    private String libFolderName = "vrnitemplate";
    private String libName;
    private ClsApiClient client;

    /**
     * Define the options specific to this sample and configure the sample using
     * command-line arguments or a config file
     *
     * @param args command line arguments passed to the sample
     */
    protected void parseArgs(String[] args) {
        // Parse the command line options or use config file
        Option libNameOption = Option.builder()
                .longOpt("contentlibraryname")
                .desc("The name of the content library "
                        + "where the library item will be"
                        + " created. Defaults to demo-local-lib")
                .required(true)
                .hasArg()
                .argName("CONTENT LIBRARY")
                .build();

        List<Option> optionList =
                Arrays.asList(libNameOption);
        super.parseArgs(optionList, args);
        this.libName = (String) parsedOptions.get("contentlibraryname");
    }

    /**
     * Setup authentication and other resources needed by the sample
     */
    protected void setup() {
        // Create the Content Library services with authenticated session
        this.client =
                new ClsApiClient(this.vapiAuthHelper.getStubFactory(),
                        sessionStubConfig);
    }

    /**
     * Run the sample
     *
     * @throws IOException
     */
    protected void run() throws Exception {
        File uploadFileService = this.vapiAuthHelper.getStubFactory().createStub(File.class, sessionStubConfig);
        UpdateSession uploadService = this.vapiAuthHelper.getStubFactory().createStub(UpdateSession.class, sessionStubConfig);
        LibraryTypes.FindSpec findSpec = new LibraryTypes.FindSpec();
        findSpec.setName(this.libName);
        List<String> libraryIds = this.client.libraryService().find(findSpec);
        assert !libraryIds.isEmpty() : "Unable to find a library with name: " + this.libName;
        String libraryId = libraryIds.get(0);
        System.out.println("Found library : " + libraryId);

        // Build the specification for the library item to be created
        ItemModel createSpec = new ItemModel();
        String name = this.libFolderName+new Random().nextInt(1000000);
        System.out.println("name : "+name);
        createSpec.setName(name);
        createSpec.setLibraryId(libraryId);
        createSpec.setType("ovf");

        // Create an UpdateSessionModel instance to track the changes you make to the item.
        UpdateSessionModel updateSessionModel = new UpdateSessionModel();



        // Create a new update session.
        String clientToken = UUID.randomUUID().toString();

        String libItemId =
                this.client.itemService().create(clientToken, createSpec);
        System.out.println("Lib Item Id : " + libItemId);
        updateSessionModel.setLibraryItemId(libItemId);
        String sessionId = uploadService.create(clientToken, updateSessionModel);

        System.out.println("Session Id : " + sessionId);

        //extract
        extractOVFAndVmdkFiles("/Users/GhoshG/Documents/office work/zero touch provisioning/ova_z/VMWare-Network-Insight-Collector.ova");
        // Create a new AddSpec instance to describe the properties of the file to be uploaded.
        FileTypes.AddSpec fileSpec = new FileTypes.AddSpec();
        fileSpec.setName("vrnic");
        fileSpec.setSourceType(FileTypes.SourceType.PULL);


        // Specify the location from which the file is uploaded to the library item.
        TransferEndpoint endpoint = new TransferEndpoint();
        endpoint.setUri(URI.create("https://s3-us-west-2.amazonaws.com/vrni-packages-archive-symphony-stag/latest/VMWare-Network-Insight-Collector.ova"));
        //endpoint.setSslCertificateThumbprint("AE:A3:C0:2F:9F:6B:FB:1B:50:97:AF:61:01:B4:13:F2:F4:71:08:28:79:6A:01:A3:DE:19:FF:EA:D6:13:05:AB");
        fileSpec.setSourceEndpoint(endpoint);
        uploadFileService.add(sessionId, fileSpec);

        UpdateSessionModel updateSessionModel1 = uploadService.get(sessionId);
        while (updateSessionModel1.getState() == UpdateSessionModel.State.ACTIVE) {
            System.out.println("Progress :" + updateSessionModel1.getClientProgress());
            Thread.sleep(60000);
            updateSessionModel1 = uploadService.get(sessionId);
        }


        UpdateSessionModel updateSessionModel2 = uploadService.get(sessionId);

        System.out.println(updateSessionModel.getState());
        System.out.println(updateSessionModel.getErrorMessage());
        // Mark the session as completed.
        uploadService.complete(sessionId);
        System.out.println("Done");
    }

    /**
     * Cleanup any resources created by the sample, logout
     */
    protected void cleanup() {
//        if (this.libItem != null) {
//            // Delete the library item
//            this.client.itemService().delete(this.libItem.getId());
//            System.out.println("Deleted library item : "
//                    + this.libItem.getId());
//        }
    }

    private void downLoadFile(final String url, final String filePath) throws Exception {
        try(ReadableByteChannel readChannel = Channels.newChannel(new URL(url).openStream());
            FileChannel writeChannel = new FileOutputStream(new java.io.File(filePath)).getChannel()) {
            writeChannel.transferFrom(readChannel, 0, Long.MAX_VALUE);
            System.out.println("File download complete");
        } catch (Exception ex) {
            System.out.println("Error when downloading file : ");
            ex.printStackTrace();
            throw  ex;
        }
        extractOVFAndVmdkFiles(filePath);
    }
    private void extractOVFAndVmdkFiles(final String filePath) throws  Exception {
        final String parentDir = new java.io.File(filePath).getParent();
        final InputStream is = new FileInputStream(filePath);
        final TarArchiveInputStream tarIs = new TarArchiveInputStream(is);
        try {
            TarArchiveEntry entry = null;
            while((entry = tarIs.getNextTarEntry()) != null) {
                final java.io.File output = new java.io.File(parentDir, entry.getName());
                final FileOutputStream fos = new FileOutputStream(output);
                IOUtils.copy(tarIs, fos);
                fos.close();
            }
        } catch (Exception ex) {
            throw ex;
        } finally {
            tarIs.close();
        }
    }

    public static void main(String[] args) throws Exception {
        /*
         * Execute the sample using the command line arguments or parameters
         * from the configuration file. This executes the following steps:
         * 1. Parse the arguments required by the sample
         * 2. Login to the server
         * 3. Setup any resources required by the sample run
         * 4. Run the sample
         * 5. Cleanup any data created by the sample run, if cleanup=true
         * 6. Logout of the server
         */
        args = new String[]{
                "--server", "vcenter.sddc-35-155-98-158.vmwarevmc.com",
                "--username", "cloudadmin@vmc.local",
                "--password", "u!JLSyv*7DKlI2a",
                "--skip-server-verification", "true",
                "--contentlibraryname", "vrniensemble"//,
                //"--truststorepath", "/Users/GhoshG/Documents/office work/zero touch provisioning/certs/ovastore"
                //"--libItemName", "vrniensemble",
                //"--libItemUrl", "vrniensemble",

        };
        new OvfUploadUrl().execute(args);
    }
}
