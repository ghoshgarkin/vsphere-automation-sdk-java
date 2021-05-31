package vmware.samples.contentlibrary.contentupdate;

import com.vmware.content.LibraryModel;
import com.vmware.content.LibraryTypes;
import com.vmware.content.library.ItemModel;
import com.vmware.content.library.ItemTypes;
import com.vmware.content.library.StorageBacking;
import com.vmware.content.library.item.TransferEndpoint;
import com.vmware.content.library.item.UpdateSession;
import com.vmware.content.library.item.UpdateSessionModel;
import com.vmware.content.library.item.updatesession.File;
import com.vmware.content.library.item.updatesession.FileTypes;
import org.apache.commons.cli.Option;
import vmware.samples.common.SamplesAbstractBase;
import vmware.samples.contentlibrary.client.ClsApiClient;
import vmware.samples.contentlibrary.crud.LibraryCrud;
import vmware.samples.vcenter.helpers.DatastoreHelper;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Created by ghoshg on 13/05/21
 */
public class CreateContentLibraryIfUnavailable extends SamplesAbstractBase {

    private String dsName;
    private String libName = "demo-local-lib";
    private ClsApiClient client;
    private LibraryModel localLibrary;
    private String libFolderName = "vrnitemplate";

    /**
     * Define the options specific to this sample and configure the sample using
     * command-line arguments or a config file
     *
     * @param args command line arguments passed to the sample
     */
    protected void parseArgs(String[] args) {
        // Parse the command line options or use config file
        Option dsNameOption = Option.builder()
                .required(true)
                .hasArg()
                .argName("DATASTORE")
                .longOpt("datastore")
                .desc("The name of the VC datastore to be used for the local "
                        + "library.")
                .build();
        Option libNameOption = Option.builder()
                .longOpt("contentlibraryname")
                .desc("OPTIONAL: The name of the local content library "
                        + "to be created.")
                .required(false)
                .hasArg()
                .argName("CONTENT LIBRARY")
                .build();

        List<Option> optionList = Arrays.asList(dsNameOption, libNameOption);
        super.parseArgs(optionList, args);
        this.dsName = (String) parsedOptions.get("datastore");
        String tmpLibName = (String) parsedOptions.get("contentlibraryname");
        this.libName = (null == tmpLibName || tmpLibName.isEmpty())
                ? this.libName : tmpLibName;
    }

    protected void setup() throws Exception {
        this.client = new ClsApiClient(this.vapiAuthHelper.getStubFactory(),
                sessionStubConfig);
    }

    protected void run() throws Exception {
        // List of visible content libraries
        // Create a FindSpec instance to set your search criteria.
        LibraryTypes.FindSpec findSpec = new LibraryTypes.FindSpec();

        // Filter the local content libraries by using a library name.
        findSpec.setName(this.libName);
        findSpec.setType(LibraryModel.LibraryType.LOCAL);
        List<String> ids = client.libraryService().find(findSpec);

        if (ids != null && ids.size() > 0) {
            System.out.println(this.libName + " library located : " + ids.get(0));
            this.localLibrary = this.client.localLibraryService().get(ids.get(0));
        } else {
            System.out.println("creating library with name : "+ this.libName);
            String libraryId = createContentLibrary();
            System.out.println(this.libName + " library created with id: " + libraryId);
            this.localLibrary = this.client.localLibraryService().get(libraryId);
        }
        uploadOvaToContentLibrary();
    }

    private String createContentLibrary() {
        //Build the storage backing for the libraries to be created
        StorageBacking storageBacking = DatastoreHelper.createStorageBacking(
                this.vapiAuthHelper, this.sessionStubConfig, this.dsName );

        // Build the specification for the library to be created
        LibraryModel createSpec = new LibraryModel();
        createSpec.setName(this.libName);
        createSpec.setDescription("Local library backed by VC datastore");
        createSpec.setType(LibraryModel.LibraryType.LOCAL);
        createSpec.setStorageBackings(Collections.singletonList(storageBacking));

        // Create a local content library backed the VC datastore using vAPIs
        String clientToken = UUID.randomUUID().toString();
        String libraryId = this.client.localLibraryService().create(clientToken, createSpec);
        System.out.println("Local library created : " + libraryId + "with name:" + this.libName);
        return libraryId;
    }

    private void uploadOvaToContentLibrary() {
        File uploadFileService = this.vapiAuthHelper.getStubFactory().createStub(File.class, sessionStubConfig);
        UpdateSession uploadService = this.vapiAuthHelper.getStubFactory().createStub(UpdateSession.class, sessionStubConfig);

        // Introduce logic to check items and decide what to do

        ItemTypes.FindSpec findSpec = new ItemTypes.FindSpec();

        // Filter the local content libraries by using a library name.
        findSpec.setName(this.libFolderName);
        findSpec.setType(ItemTypes.RESOURCE_TYPE);
        List<String> libItemIds = this.client.itemService().find(findSpec);
        String clientToken = UUID.randomUUID().toString();
        String libItemId = "";
        if (libItemIds != null && libItemIds.size() > 0) {
            System.out.println(this.libFolderName + " item located : " + libItemIds.get(0));
            libItemId = libItemIds.get(0);
        } else {
            // Build the specification for the library item to be created
            ItemModel createSpec = new ItemModel();
            createSpec.setName(this.libFolderName);
            createSpec.setLibraryId(this.localLibrary.getId());
            createSpec.setType("ovf");
            // Create a new update session.
            libItemId = this.client.itemService().create(clientToken, createSpec);
            System.out.println(this.libFolderName + " created item : " + libItemId);
            // Create an UpdateSessionModel instance to track the changes you make to the item.
            UpdateSessionModel updateSessionModel = new UpdateSessionModel();

            System.out.println("Lib Item Id : " + libItemId);
            updateSessionModel.setLibraryItemId(libItemId);
            String sessionId = uploadService.create(clientToken, updateSessionModel);

            System.out.println("Session Id : " + sessionId);

            // Create a new AddSpec instance to describe the properties of the file to be uploaded.
            FileTypes.AddSpec fileSpec = new FileTypes.AddSpec();
            fileSpec.setName("vrnic");
            fileSpec.setSourceType(FileTypes.SourceType.PULL);

            // Specify the location from which the file is uploaded to the library item.
            TransferEndpoint endpoint = new TransferEndpoint();
            endpoint.setUri(URI.create("https://s3-us-west-2.amazonaws.com/vrni-packages-archive-symphony-stag/latest/VMWare-Network-Insight-Collector.ova"));
            fileSpec.setSourceEndpoint(endpoint);
            uploadFileService.add(sessionId, fileSpec);


            // Mark the session as completed.
            uploadService.complete(sessionId);
        }
        System.out.println("Done");
    }

    protected void cleanup() throws Exception {
        if (localLibrary != null && false) {
            // Delete the content library
            this.client.localLibraryService().
                    delete(this.localLibrary.getId());
            System.out.println("Deleted Local Content Library : "
                    + this.localLibrary.getId());
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
                "--contentlibraryname", "gg-ensemble",
                "--datastore", "WorkloadDatastore"
        };
        new CreateContentLibraryIfUnavailable().execute(args);
    }
}