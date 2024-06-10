package lu.snt.serval.astdiff;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.*;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.*;

public class MainTest {

    @Test
    public void testConnectGithub() {
        // Arrange
        Main main = new Main();

        // Act
        GitHub github = main.connectGithub();

        // Assert
        assertNotNull(github);
        assertTrue(github.isCredentialValid());
    }
}