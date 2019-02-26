package se.yolean.kafka.keyvalue.onupdate;

import static org.junit.jupiter.api.Assertions.*;

import javax.ws.rs.core.Response;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import se.yolean.kafka.keyvalue.onupdate.ResponseSuccessCriteria;
import se.yolean.kafka.keyvalue.onupdate.ResponseSuccessCriteriaStatus200or204;

class ResponseSuccessCriteriaStatus200or204Test {

  private ResponseSuccessCriteria criteria = new ResponseSuccessCriteriaStatus200or204();

  @Test
  void testIsSuccess() {
    Response response = Mockito.mock(Response.class);
    Mockito.when(response.getStatus()).thenReturn(200);
    assertTrue(criteria.isSuccess(response));
  }

  @Test
  void testIsSuccess204() {
    Response response = Mockito.mock(Response.class);
    Mockito.when(response.getStatus()).thenReturn(204);
    assertTrue(criteria.isSuccess(response));
  }

  @Test
  void testIsSuccessNot201() {
    Response response = Mockito.mock(Response.class);
    Mockito.when(response.getStatus()).thenReturn(201);
    assertFalse(criteria.isSuccess(response));
  }

  @Test
  void testNullResponse() {
    try {
      criteria.isSuccess(null);
      fail("Should have thrown on null response");
    } catch (IllegalArgumentException e) {
      assertEquals("Response is null", e.getMessage());
    }
  }

}
