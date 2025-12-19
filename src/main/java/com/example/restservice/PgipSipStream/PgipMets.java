package com.example.restservice.PgipSipStream;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import java.io.OutputStream;
import java.net.FileNameMap;
import java.net.URLConnection;
import java.util.List;
import java.util.Optional;
import javax.xml.datatype.DatatypeConfigurationException;
import org.apache.commons.lang3.StringUtils;
import org.roda_project.commons_ip.utils.IPEnums.IPType;
import org.roda_project.commons_ip.utils.IPException;
import org.roda_project.commons_ip.utils.METSEnums;
import org.roda_project.commons_ip2.mets_v1_12.beans.DivType;
import org.roda_project.commons_ip2.mets_v1_12.beans.DivType.Mptr;
import org.roda_project.commons_ip2.mets_v1_12.beans.FileType;
import org.roda_project.commons_ip2.mets_v1_12.beans.MdSecType;
import org.roda_project.commons_ip2.mets_v1_12.beans.Mets;
import org.roda_project.commons_ip2.mets_v1_12.beans.MetsType;
import org.roda_project.commons_ip2.model.IPConstants;
import org.roda_project.commons_ip2.model.IPContentInformationType;
import org.roda_project.commons_ip2.model.IPContentType;
import org.roda_project.commons_ip2.model.IPDescriptiveMetadata;
import org.roda_project.commons_ip2.model.IPFileInterface;
import org.roda_project.commons_ip2.model.IPHeader;
import org.roda_project.commons_ip2.model.IPRepresentation;
import org.roda_project.commons_ip2.model.MetsWrapper;
import org.roda_project.commons_ip2.model.impl.eark.EARKMETSCreator;
import org.roda_project.commons_ip2.model.impl.eark.METSGeneratorFactory;
import org.roda_project.commons_ip2.utils.METSUtils;
import org.roda_project.commons_ip2.utils.Utils;

public class PgipMets {

  public static final FileNameMap FILE_NAME_MAP = URLConnection.getFileNameMap();
  private static final String DEFAULT_SIP_VERSION = "2.1.0";
  private final Mets mets;
  private final MetsWrapper metsWrapper;
  private final boolean isRootLevelMets;

  private final String profile = IPConstants.SIP_SPEC_PROFILE;
  private List<String> ancestors;
  private IPHeader header = new IPHeader();
  private IPType type = IPType.SIP;

  private String getProfile() {
    return profile;
  }

  private List<String> getAncestors() {
    return ancestors;
  }

  private IPHeader getHeader() {
    return header;
  }

  private String getType() {
    return type.toString();
  }

  private IPContentType getContentType() {
    return IPContentType.getMIXED();
  }

  private IPContentInformationType getContentInformationType() {
    return IPContentInformationType.getMIXED();
  }

  public PgipMets(final String id, final String description, final Optional<List<String>> ancestors,
      final boolean isRootLevelMets) throws IPException {
    this.mets = new Mets();
    this.isRootLevelMets = isRootLevelMets;

    METSGeneratorFactory factory = new METSGeneratorFactory();
    EARKMETSCreator metsCreator = factory.getGenerator(DEFAULT_SIP_VERSION);

    this.metsWrapper = metsCreator.generateMETS(
        id,
        description,
        this.getProfile(),
        isRootLevelMets,
        ancestors,
        null,
        this.getHeader(),
        this.getType(),
        this.getContentType(),
        this.getContentInformationType(),
        true,
        false,
        true,
        false,
        false,
        isRootLevelMets, // isRepresentations, does the mets contain any representations
        !isRootLevelMets // isRepresentationsData, does the mets contain representation data
    );
  }

  public static PgipMets newIpMets(final String id, final String description, final Optional<List<String>> ancestors)
      throws IPException {
    return new PgipMets(id, description, ancestors, true);
  }

  public static PgipMets newRepresentationMets(final IPRepresentation representation)
      throws IPException {
    PgipMets pgipMets = new PgipMets(representation.getRepresentationID(), representation.getDescription(),
        Optional.empty(), false);

    pgipMets.metsWrapper.getMainDiv().setTYPE(representation.getStatus().asString());

    return pgipMets;
  }

  // Based on EARKMETSCreator.addMetadataToMETS
  // https://github.com/keeps/commons-ip/blob/980a639bc9cac561433c31d338770c3f6cfbf679/src/main/java/org/roda_project/commons_ip2/model/impl/eark/EARKMETSCreator.java#L407
  public void addDescriptiveMetadata(final IPDescriptiveMetadata metadata) throws IPException {
    final IPFileInterface metadataFile = metadata.getMetadata();

    final MdSecType dmdSec = new MdSecType();
    dmdSec.setSTATUS(metadata.getMetadataStatus().toString());
    dmdSec.setID(Utils.generateRandomAndPrefixedUUID());

    final MdSecType.MdRef mdRef = PgipMetsUtils.createMdRef(metadata.getId(),
        metadataFile.getPath().toString());

    final String mdType = metadata.getMetadataType().getType().getType();
    final String mdOtherType = metadata.getMetadataType().getOtherType();

    mdRef.setMDTYPE(mdType);
    if (StringUtils.isNotBlank(mdOtherType)) {
      mdRef.setOTHERMDTYPE(mdOtherType);
    }
    mdRef.setMDTYPEVERSION(metadata.getMetadataVersion());

    mdRef.setMIMETYPE(FILE_NAME_MAP.getContentTypeFor(metadataFile.getFileName()));

    try {
      mdRef.setCREATED(Utils.getCurrentCalendar());
    } catch (DatatypeConfigurationException e) {
      throw new IPException("Error getting current calendar", e);
    }

    if (metadataFile instanceof PgipIPFile pgipIpFile) {
      mdRef.setSIZE(pgipIpFile.getSize());
      mdRef.setCHECKSUM(pgipIpFile.getChecksum());
      mdRef.setCHECKSUMTYPE(IPConstants.CHECKSUM_SHA_256_ALGORITHM);
    }

    dmdSec.setCREATED(mdRef.getCREATED());

    metsWrapper.getMetadataDiv().getDMDID().add(dmdSec);

    dmdSec.setMdRef(mdRef);
    metsWrapper.getMets().getDmdSec().add(dmdSec);
  }

  public void addRepresentation(final IPRepresentation representation, final PgipIPFile metsIpFile) {
    final DivType.Mptr mptr = new DivType.Mptr();
    mptr.setLOCTYPE(METSEnums.LocType.URL.toString());
    mptr.setType(IPConstants.METS_TYPE_SIMPLE);
    mptr.setHref(METSUtils.encodeHref(metsIpFile.getPath().toString()));

    // create file
    final FileType fileType = new FileType();
    fileType.setID(Utils.generateRandomAndPrefixedFileID());

    // add to file group and then to file section
    final MetsType.FileSec.FileGrp fileGrp = new MetsType.FileSec.FileGrp();
    fileGrp.setID(Utils.generateRandomAndPrefixedUUID());
    fileGrp.setUSE(IPConstants.REPRESENTATIONS_WITH_FIRST_LETTER_CAPITAL + "/" + representation.getRepresentationID());

    final FileType.FLocat fileLocation = METSUtils.createFileLocation(metsIpFile.getPath().toString());
    fileType.getFLocat().add(fileLocation);
    fileGrp.getFile().add(fileType);
    metsWrapper.getMets().getFileSec().getFileGrp().add(fileGrp);

    // set mets pointer
    final DivType representationDiv = new DivType();
    representationDiv.setID(Utils.generateRandomAndPrefixedUUID());
    representationDiv.setLABEL(IPConstants.REPRESENTATIONS_WITH_FIRST_LETTER_CAPITAL + "/" + representation.getRepresentationID());
    representationDiv.getMptr().add(mptr);
    mptr.setTitle(fileGrp.getID());

    metsWrapper.getMainDiv().getDiv().add(representationDiv);
  }

  // Based on EARKMETSCreator.addSchemaFileToMETS
  // https://github.com/keeps/commons-ip/blob/980a639bc9cac561433c31d338770c3f6cfbf679/src/main/java/org/roda_project/commons_ip2/model/impl/eark/EARKMETSCreator.java#L582
  public void addSchema(PgipIPFile ipFile) throws IPException {
    final FileType file = new FileType();
    file.setID(Utils.generateRandomAndPrefixedFileID());

    file.setMIMETYPE(FILE_NAME_MAP.getContentTypeFor(ipFile.getFileName()));

    try {
      file.setCREATED(Utils.getCurrentCalendar());
    } catch (DatatypeConfigurationException e) {
      throw new IPException("Error getting current calendar", e);
    }

    file.setSIZE(ipFile.getSize());
    file.setCHECKSUM(ipFile.getChecksum());
    file.setCHECKSUMTYPE(IPConstants.CHECKSUM_SHA_256_ALGORITHM);

    // add to file section
    final FileType.FLocat fileLocation = METSUtils.createFileLocation(ipFile.getPath().toString());
    file.getFLocat().add(fileLocation);
    if (metsWrapper.getSchemasFileGroup() != null) {
      metsWrapper.getSchemasFileGroup().getFile().add(file);
    }

    // add to struct map
    if (metsWrapper.getSchemasDiv() != null && metsWrapper.getSchemasDiv().getFptr().isEmpty()) {
      final DivType.Fptr fptr = new DivType.Fptr();
      fptr.setFILEID(metsWrapper.getSchemasFileGroup());
      metsWrapper.getSchemasDiv().getFptr().add(fptr);
    }
  }



  public void addRepresentationDataFile(PgipIPFile ipFile) throws IPException {
    final FileType file = new FileType();
    file.setID(Utils.generateRandomAndPrefixedFileID());

    file.setMIMETYPE(FILE_NAME_MAP.getContentTypeFor(ipFile.getFileName()));

    try {
      file.setCREATED(Utils.getCurrentCalendar());
    } catch (DatatypeConfigurationException e) {
      throw new IPException("Error getting current calendar", e);
    }

    file.setSIZE(ipFile.getSize());
    file.setCHECKSUM(ipFile.getChecksum());
    file.setCHECKSUMTYPE(IPConstants.CHECKSUM_SHA_256_ALGORITHM);

    // add to file section
    final FileType.FLocat fileLocation = METSUtils.createFileLocation(ipFile.getPath().toString());
    file.getFLocat().add(fileLocation);
    metsWrapper.getDataFileGroup().getFile().add(file);

    // add to struct map
    if (metsWrapper.getDataDiv().getFptr().isEmpty()) {
      final DivType.Fptr fptr = new DivType.Fptr();
      fptr.setFILEID(metsWrapper.getDataFileGroup());
      metsWrapper.getDataDiv().getFptr().add(fptr);
    }
  }

  public void marshallMETS(OutputStream outputStream) throws JAXBException {
    JAXBContext context = JAXBContext.newInstance(Mets.class);
    Marshaller m = context.createMarshaller();
    m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

    final String schemas_path = isRootLevelMets ? "schemas/" : "../../schemas/";

    m.setProperty(Marshaller.JAXB_SCHEMA_LOCATION,
        "http://www.loc.gov/METS/ " + schemas_path + IPConstants.SCHEMA_METS_FILENAME_WITH_VERSION
            + " http://www.w3.org/1999/xlink " + schemas_path + IPConstants.SCHEMA_XLINK_FILENAME
            + " https://dilcis.eu/XML/METS/CSIPExtensionMETS " + schemas_path
            + IPConstants.SCHEMA_EARK_CSIP_FILENAME
            + " https://dilcis.eu/XML/METS/SIPExtensionMETS " + schemas_path
            + IPConstants.SCHEMA_EARK_SIP_FILENAME);

    m.marshal(metsWrapper.getMets(), outputStream);
  }
}
