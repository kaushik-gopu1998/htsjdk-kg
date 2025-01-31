package htsjdk.tribble;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import htsjdk.HtsjdkTest;
import htsjdk.samtools.FileTruncatedException;
import htsjdk.samtools.util.IOUtilTest;
import htsjdk.samtools.util.TestUtil;
import htsjdk.tribble.bed.BEDCodec;
import htsjdk.tribble.bed.BEDFeature;
import htsjdk.tribble.readers.LineIterator;
import htsjdk.variant.VariantBaseTest;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.vcf.VCFCodec;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderVersion;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.util.function.Function;

import static org.testng.Assert.*;

/**
 * @author jacob
 * @date 2013-Apr-10
 */
public class AbstractFeatureReaderTest extends HtsjdkTest {

    final static String HTTP_INDEXED_VCF_PATH = TestUtil.BASE_URL_FOR_HTTP_TESTS + "ex2.vcf";
    final static String LOCAL_MIRROR_HTTP_INDEXED_VCF_PATH = VariantBaseTest.variantTestDataRoot + "ex2.vcf";

    //the "mangled" versions of the files have an extra byte added to the front of the file that makes them invalid
    private static final String TEST_PATH = "src/test/resources/htsjdk/tribble/AbstractFeatureReaderTest/";
    private static final String MANGLED_VCF = TEST_PATH + "mangledBaseVariants.vcf";
    private static final String MANGLED_VCF_INDEX = TEST_PATH + "mangledBaseVariants.vcf.idx";
    private static final String VCF = TEST_PATH + "baseVariants.vcf";
    private static final String VCF_INDEX = TEST_PATH + "baseVariants.vcf.idx";
    private static final String VCF_TABIX_BLOCK_GZIPPED = TEST_PATH + "baseVariants.vcf.gz";
    private static final String VCF_TABIX_INDEX = TEST_PATH + "baseVariants.vcf.gz.tbi";
    private static final String MANGLED_VCF_TABIX_BLOCK_GZIPPED = TEST_PATH + "baseVariants.mangled.vcf.gz";
    private static final String MANGLED_VCF_TABIX_INDEX = TEST_PATH + "baseVariants.mangled.vcf.gz.tbi";
    private static final String CORRUPTED_VCF_INDEX = TEST_PATH + "corruptedBaseVariants.vcf.idx";

    //wrapper which skips the first byte of a file and leaves the rest unchanged
    private static final Function<SeekableByteChannel, SeekableByteChannel> WRAPPER = SkippingByteChannel::new;

    /**
     * Asserts readability and correctness of VCF over HTTP.  The VCF is indexed and requires and index.
     */
    @Test
    public void testVcfOverHTTP() throws IOException {
        final VCFCodec codec = new VCFCodec();
        final AbstractFeatureReader<VariantContext, LineIterator> featureReaderHttp =
                AbstractFeatureReader.getFeatureReader(HTTP_INDEXED_VCF_PATH, codec, true); // Require an index to
        final AbstractFeatureReader<VariantContext, LineIterator> featureReaderLocal =
                AbstractFeatureReader.getFeatureReader(LOCAL_MIRROR_HTTP_INDEXED_VCF_PATH, codec, false);
        final CloseableTribbleIterator<VariantContext> localIterator = featureReaderLocal.iterator();
        for (final Feature feat : featureReaderHttp.iterator()) {
            assertEquals(feat.toString(), localIterator.next().toString());
        }
        assertFalse(localIterator.hasNext());
    }

    @Test(groups = "ftp")
    public void testLoadBEDFTP() throws Exception {
        final String path = "ftp://ftp.broadinstitute.org/distribution/igv/TEST/cpgIslands with spaces.hg18.bed";
        final BEDCodec codec = new BEDCodec();
        final AbstractFeatureReader<BEDFeature, LineIterator> bfs = AbstractFeatureReader.getFeatureReader(path, codec, false);
        for (final Feature feat : bfs.iterator()) {
            assertNotNull(feat);
        }
    }

    @Test(dataProvider = "blockCompressedExtensionExtensionStrings", dataProviderClass = IOUtilTest.class)
    public void testBlockCompressionExtensionString(final String testString, final boolean expected) {
        Assert.assertEquals(AbstractFeatureReader.hasBlockCompressedExtension(testString), expected);
    }

    @Test(dataProvider = "blockCompressedExtensionExtensionStrings", dataProviderClass = IOUtilTest.class)
    public void testBlockCompressionExtensionFile(final String testString, final boolean expected) {
        Assert.assertEquals(AbstractFeatureReader.hasBlockCompressedExtension(new File(testString)), expected);
    }

    @Test(dataProvider = "blockCompressedExtensionExtensionURIStrings", dataProviderClass = IOUtilTest.class)
    public void testBlockCompressionExtension(final String testURIString, final boolean expected) {
        URI testURI = URI.create(testURIString);
        Assert.assertEquals(AbstractFeatureReader.hasBlockCompressedExtension(testURI), expected);
    }

    @Test(dataProvider = "blockCompressedExtensionExtensionURIStrings", dataProviderClass = IOUtilTest.class)
    public void testBlockCompressionExtensionStringVersion(final String testURIString, final boolean expected) {
        Assert.assertEquals(AbstractFeatureReader.hasBlockCompressedExtension(testURIString), expected);
    }
    @Test(groups = "optimistic_vcf_4_4")
    public void testVCF4_4Optimistic() {
        final AbstractFeatureReader<VariantContext, ?> fr = AbstractFeatureReader.getFeatureReader(
                Paths.get("src/test/resources/htsjdk/variant/", "VCF4_4HeaderTest.vcf").toString(),
                new VCFCodec(),
                false);
        final VCFHeader vcfHeader = (VCFHeader) fr.getHeader();
        Assert.assertEquals(vcfHeader.getVCFHeaderVersion(), VCFHeaderVersion.VCF4_3);
    }

    @DataProvider(name = "vcfFileAndWrapperCombinations")
    private static Object[][] vcfFileAndWrapperCombinations(){
        return new Object[][] {
                {VCF, VCF_INDEX, null, null},
                {MANGLED_VCF, MANGLED_VCF_INDEX, WRAPPER, WRAPPER},
                {VCF, MANGLED_VCF_INDEX, null, WRAPPER},
                {MANGLED_VCF, VCF_INDEX, WRAPPER, null},
                {MANGLED_VCF_TABIX_BLOCK_GZIPPED, MANGLED_VCF_TABIX_INDEX, WRAPPER, WRAPPER},
                {VCF_TABIX_BLOCK_GZIPPED, MANGLED_VCF_TABIX_INDEX, null, WRAPPER},
                {MANGLED_VCF_TABIX_BLOCK_GZIPPED, VCF_TABIX_INDEX, WRAPPER, null},
                {VCF_TABIX_BLOCK_GZIPPED, VCF_TABIX_INDEX, null, null},
        };
    }

    @Test(dataProvider = "vcfFileAndWrapperCombinations")
    public void testGetFeatureReaderWithPathAndWrappers(String file, String index,
                                                        Function<SeekableByteChannel, SeekableByteChannel> wrapper,
                                                        Function<SeekableByteChannel, SeekableByteChannel> indexWrapper) throws IOException {
        try(FileSystem fs = Jimfs.newFileSystem("test", Configuration.unix());
            final AbstractFeatureReader<VariantContext, ?> featureReader = getFeatureReader(file, index, wrapper,
                                                                                            indexWrapper,
                                                                                            new VCFCodec(),
                                                                                            fs)){
            Assert.assertTrue(featureReader.hasIndex());
            Assert.assertEquals(featureReader.iterator().toList().size(), 26);
            Assert.assertEquals(featureReader.query("1", 190, 210).toList().size(), 3);
            Assert.assertEquals(featureReader.query("2", 190, 210).toList().size(), 1);
        }
    }

    @DataProvider(name = "failsWithoutWrappers")
    public static Object[][] failsWithoutWrappers(){
        return new Object[][] {
                {MANGLED_VCF, MANGLED_VCF_INDEX},
                {VCF, CORRUPTED_VCF_INDEX},
                {VCF, MANGLED_VCF_INDEX},
                {MANGLED_VCF, VCF_INDEX},
                {MANGLED_VCF_TABIX_BLOCK_GZIPPED, MANGLED_VCF_TABIX_INDEX},
                {VCF_TABIX_BLOCK_GZIPPED, MANGLED_VCF_TABIX_INDEX},
                {MANGLED_VCF_TABIX_BLOCK_GZIPPED, VCF_TABIX_INDEX},
        };
    }

    @Test(dataProvider = "failsWithoutWrappers", expectedExceptions = {TribbleException.class, FileTruncatedException.class})
    public void testFailureIfNoWrapper(String file, String index) throws IOException {
        try(final FileSystem fs = Jimfs.newFileSystem("test", Configuration.unix());
            final FeatureReader<?> reader = getFeatureReader(file, index, null, null, new VCFCodec(), fs)){
            // should have exploded by now
        }
    }

    private static <T extends Feature> AbstractFeatureReader<T, ?> getFeatureReader(String vcf, String index,
                                                                                    Function<SeekableByteChannel, SeekableByteChannel> wrapper,
                                                                                    Function<SeekableByteChannel, SeekableByteChannel> indexWrapper,
                                                                                    FeatureCodec<T, ?> codec,
                                                                                    FileSystem fileSystem) throws IOException {
        final Path vcfInJimfs = TestUtils.getTribbleFileInJimfs(vcf, index, fileSystem);
        return AbstractFeatureReader.getFeatureReader(
                vcfInJimfs.toUri().toString(),
                null,
                codec,
                true,
                wrapper,
                indexWrapper);
    }

    /**
     * skip the first byte of a SeekableByteChannel
     */
    private static class SkippingByteChannel implements SeekableByteChannel{
        private final int toSkip;
        private final SeekableByteChannel input;

       private SkippingByteChannel(SeekableByteChannel input) {
           this.toSkip = 1;
           try {
               this.input = input;
               input.position(toSkip);
           } catch (final IOException e){
               throw new RuntimeException(e);
           }
       }

       @Override
        public boolean isOpen() {
            return input.isOpen();
        }

        @Override
        public void close() throws IOException {
            input.close();
        }

        @Override
        public int read(ByteBuffer dst) throws IOException {
           return input.read(dst);
        }

        @Override
        public int write(ByteBuffer src) throws IOException {
            throw new UnsupportedOperationException("Read only");
        }

        @Override
        public long position() throws IOException {
            return input.position() - toSkip;
        }

        @Override
        public SeekableByteChannel position(long newPosition) throws IOException {
            if (newPosition < 0 ){
                throw new RuntimeException("negative position not allowed");
            }
            return input.position( newPosition + toSkip);
        }

        @Override
        public long size() throws IOException {
            return input.size() - toSkip;
        }

        @Override
        public SeekableByteChannel truncate(long size) throws IOException {
            return input.truncate(size + toSkip);
        }
    }

    @DataProvider
    public Object[][] getVcfRedirects(){
        return new Object[][]{
          {VCFRedirectCodec.REDIRECTING_CODEC_TEST_FILE_ROOT + "vcf.redirect"},
          {VCFRedirectCodec.REDIRECTING_CODEC_TEST_FILE_ROOT + "vcf.gz.redirect"}
        };
    }

    /**
     * Test a codec that uses {@link FeatureCodec#getPathToDataFile(String)} in order to specify a data file that's
     * different than the file it identifies with {@link FeatureCodec#canDecode}).
     */
    @Test(dataProvider = "getVcfRedirects")
    public void testCodecWithGetPathToDataFile(String vcfRedirect) throws IOException {
        final VCFRedirectCodec vcfRedirectCodec = new VCFRedirectCodec();
        final String vcf = VCFRedirectCodec.REDIRECTING_CODEC_TEST_FILE_ROOT + "dataFiles/test.vcf";
        Assert.assertTrue(vcfRedirectCodec.canDecode(vcfRedirect), "should have been able to decode " + vcfRedirect);
        try(FeatureReader<VariantContext> redirectReader = AbstractFeatureReader.getFeatureReader(vcfRedirect, vcfRedirectCodec, false);
            FeatureReader<VariantContext> directReader = AbstractFeatureReader.getFeatureReader(vcf, new VCFCodec(), false)){
            Assert.assertEquals(redirectReader.getHeader().toString(), directReader.getHeader().toString());
            final int redirectVcfSize = redirectReader.iterator().toList().size();
            Assert.assertTrue( redirectVcfSize > 0, "iterator found " + redirectVcfSize + " records");
            Assert.assertEquals(redirectVcfSize, directReader.iterator().toList().size());

            final int redirectQuerySize = redirectReader.query("20", 1, 20000).toList().size();
            Assert.assertTrue(redirectQuerySize > 0, "query found " + redirectVcfSize + " records");
            Assert.assertEquals(redirectQuerySize, directReader.query("20", 1, 20000).toList().size() );
        }
    }

}
