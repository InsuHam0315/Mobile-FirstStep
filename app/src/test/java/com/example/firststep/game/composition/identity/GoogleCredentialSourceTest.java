package com.example.firststep.game.composition.identity;

import static org.junit.Assert.assertEquals;

import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialUnknownException;
import androidx.credentials.exceptions.NoCredentialException;
import com.example.firststep.game.auth.Failure;
import org.junit.Test;

public final class GoogleCredentialSourceTest {
  @Test
  public void credentialErrorsRetainDistinctOutcomes() {
    assertEquals(
        Failure.NO_CREDENTIAL, GoogleCredentialSource.classifyError(new NoCredentialException()));
    assertEquals(
        Failure.CANCELED,
        GoogleCredentialSource.classifyError(new GetCredentialCancellationException()));
    assertEquals(
        Failure.UNAVAILABLE, GoogleCredentialSource.classifyError(new GetCredentialUnknownException()));
  }
}
