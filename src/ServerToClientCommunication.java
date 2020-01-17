package src;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;

import src.ReadingStrategies.ContentLengthReadingStrategy;
import src.WritingStrategies.CachedWritingStrategy;
import src.WritingStrategies.RawPassthroughWritingStrategy;
import src.WritingStrategies.WritingStrategy;

/**
 * ClientToServerCommunication
 */
public class ServerToClientCommunication extends Thread {
  private InputStream inFromServer;

  private OutputStream outToClient;

  private RequestLineParser requestLineParser;

  private OnServerClientCommunicationEnd onServerClientCommunicationEnd;

  private LastRequest lastRequest;

  public ServerToClientCommunication(
    InputStream inFromServer,
    OutputStream outToClient,
    RequestLineParser requestLineParser,
    OnServerClientCommunicationEnd onServerClientCommunicationEnd,
    LastRequest lastRequest
  ) {
    this.inFromServer = inFromServer;
    this.outToClient = outToClient;
    this.requestLineParser = requestLineParser;
    this.onServerClientCommunicationEnd = onServerClientCommunicationEnd;
    this.lastRequest = lastRequest;
  }

  private void pipeHttpResponse() throws IOException, EndOfRequestException {
    ResponseLineParser responseLineParser = new ResponseLineParser(inFromServer);

    RequestLineParser request = lastRequest.getLastRequest();
    System.out.println(request.getHost() + " " + request.getPort() + " " + request.getPath());

    HeadersParser headersParser = new HeadersParser(inFromServer);

    WritingStrategy[] writingStrategies = {
      new CachedWritingStrategy(request),
      new RawPassthroughWritingStrategy(outToClient)
    };

    ContentLengthReadingStrategy readingStrategy = new ContentLengthReadingStrategy(
      inFromServer,
      headersParser,
      responseLineParser,
      writingStrategies
    );

    readingStrategy.read();

    this.pipeHttpResponse();
  }

  public void run() {
    try {
      if (requestLineParser.getIsHttps()) {
        outToClient.write(
          ProxyResponse.getSuccessResponse(
            requestLineParser.getHttpVersion()
          ).getBytes()
        );

        new DataPipe(inFromServer, outToClient);
      } else {
        this.pipeHttpResponse();
      }
    } catch(SocketException e) {
    } catch(IOException e) {
      e.printStackTrace();
    } catch(EndOfRequestException e) {
    } catch(Exception e) {
      // TODO java.lang.ArrayIndexOutOfBoundsException: Index 1 out of bounds for length 1
      e.printStackTrace();
    }

    try {
      outToClient.close();
    } catch (Exception e) {
      e.printStackTrace();
    }

    onServerClientCommunicationEnd.onEnd();
  }
}