package com.example.restservice.PgipSipStream;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import org.roda_project.commons_ip.utils.METSEnums;
import org.roda_project.commons_ip2.mets_v1_12.beans.MdSecType;
import org.roda_project.commons_ip2.mets_v1_12.beans.Mets;
import org.roda_project.commons_ip2.model.IPConstants;
import org.roda_project.commons_ip2.utils.METSUtils;

public class PgipMetsUtils {
  /*
  public static IPDescriptiveMetadata createDescriptiveMetadata(final Path path, final String metadataType, final String metadataVersion) {
    final PgipIPFile ipFile = new PgipIPFile(path);
    return new IPDescriptiveMetadata(ipFile, new MetadataType(metadataType), metadataVersion);
  }
  */

  public static MdSecType.MdRef createMdRef(final String id, final String metadataPath) {
    final MdSecType.MdRef mdRef = new MdSecType.MdRef();
    mdRef.setID(METSEnums.FILE_ID_PREFIX + escapeNCName(id));
    mdRef.setType(IPConstants.METS_TYPE_SIMPLE);
    mdRef.setLOCTYPE(METSEnums.LocType.URL.toString());
    mdRef.setHref(METSUtils.encodeHref(metadataPath));
    return mdRef;
  }

  public static String escapeNCName(final String id) {
    return id.replaceAll("[:@$%&/+,;\\s]", "_");
  }

  // Based on METSUtils.marshallMETS
  public static void marshallMETS(Mets mets, boolean rootMETS) throws JAXBException {
    JAXBContext context = JAXBContext.newInstance(Mets.class);
    Marshaller m = context.createMarshaller();
    m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

    final String schemas_path = rootMETS ? "schemas/" : "../../schemas/";

    m.setProperty(Marshaller.JAXB_SCHEMA_LOCATION,
        "http://www.loc.gov/METS/ " + schemas_path + IPConstants.SCHEMA_METS_FILENAME_WITH_VERSION
            + " http://www.w3.org/1999/xlink " + schemas_path + IPConstants.SCHEMA_XLINK_FILENAME
            + " https://dilcis.eu/XML/METS/CSIPExtensionMETS " + schemas_path
            + IPConstants.SCHEMA_EARK_CSIP_FILENAME
            + " https://dilcis.eu/XML/METS/SIPExtensionMETS " + schemas_path
            + IPConstants.SCHEMA_EARK_SIP_FILENAME);
  }

  /*
  public void createSipMets() {
    final Mets mets = new Mets();
    MetsWrapper metsWrapper = new MetsWrapper(mets, null);

    IPDescriptiveMetadata pgipDescriptiveMetadata = createPgipDescriptiveMetadata();
    IPFileInterface file = pgipDescriptiveMetadata.getMetadata();

    String descriptiveFilePath =
        IPConstants.DESCRIPTIVE_FOLDER + ModelUtils.getFoldersFromList(file.getRelativeFolders())
            + file.getFileName();

    addDescriptiveMetadataToMets(metsWrapper, pgipDescriptiveMetadata, descriptiveFilePath);
  }
  */
}
