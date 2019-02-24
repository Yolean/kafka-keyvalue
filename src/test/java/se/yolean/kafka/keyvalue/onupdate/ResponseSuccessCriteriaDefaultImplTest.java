package se.yolean.kafka.keyvalue.onupdate;

import static org.junit.jupiter.api.Assertions.*;

import javax.ws.rs.core.Response;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ResponseSuccessCriteriaDefaultImplTest {

  private ResponseSuccessCriteria criteria = new ResponseSuccessCriteriaDefaultImpl();

  @Test
  void testIsSuccess() {
    Response response = Mockito.mock(Response.class);
    Mockito.when(response.getStatus()).thenReturn(200);
    assertTrue(criteria.isSuccess(response));
  }

}
