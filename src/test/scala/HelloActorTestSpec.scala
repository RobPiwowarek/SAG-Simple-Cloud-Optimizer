import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActors, TestKit}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import pl.rpw.core.HelloActor

class HelloActorTestSpec()
  extends TestKit(ActorSystem("HelloSpec"))
    with ImplicitSender
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll {

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  private val helloActor: ActorRef = system.actorOf(Props[HelloActor], name = "helloactor")

  "An Echo actor" must {

    "send back messages unchanged" in {
      val echo = system.actorOf(TestActors.echoActorProps)
      echo ! "hello world"
      expectMsg("hello world")
    }

  }

  "A Hello actor" must {

    "send back hello" when {
      "receiving hello" in {
        helloActor ! "hello"
        expectMsg("hello back at you")
      }
    }

    "send back om nom nom nom" when {
      "receiving me liek cookiez" in {
        helloActor ! "me liek cookiez"
        expectMsg("om nom nom nom")
      }
    }

    "send back huh?" when {
      "receiving random gibberish" in {
        helloActor ! "random gibberish"
        expectMsg("huh?")
      }
    }
  }
}