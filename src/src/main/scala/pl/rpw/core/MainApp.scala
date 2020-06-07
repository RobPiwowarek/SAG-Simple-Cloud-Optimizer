package pl.rpw.core

import akka.actor.{ActorSystem, Props}

object MainApp extends App {

      /**
      Co zrobić przed startem:
      jeśli byłby prolem ze złą ścieżką podczas wczytywania pliku zmienić
      w klasie LocalAgent zmienić zmienną rootFilePath na odpowiednią

       Jak korzystać z agenta:
       - tworzymy wiadomością StartAgent z odpowiednimi amplitodami w argumentach
       tutaj właściwie nie trzeba odbierać żadnej wiadomości zwrotnej

       - dodajemy maszynę wirtualną poprzed wysłanie wiadomości GetNewVM, w argumencie podając
       jak bardzo wynik ma być większy lub mniejszy niż ten zwracany przez model. Myślę, że 1.0 może zostać
       Tutaj w odpowiedzi dostaniecie mapę z przewidywanym zużyciem zasobów, która wygląda następująco:
       Map(cpu -> Double, gpu -> Double, disk -> Double)

       */

      val system = ActorSystem("LocalAgent")
      val localAgent = system.actorOf(Props[LocalAgent], name = "LocalAgent")
      val cpu: Int = 2
      val gpu: Int = 3
      val disk: Int = 4

      //tutaj tworzymy agenta, cpu,gpu,disk to amplituda sinusa odpowiednich zasobów
      localAgent ! StartAgent(cpu, gpu, disk)

      //tak tworzymy nową maszynę wirtualną
      localAgent ! GetNewVM(1.0)

      localAgent ! "ShutDown"
}

case class StartAgent(cpu: Int, gpu: Int, disk: Int)

// conservativenessRate is a value which says how conservative the prediction should be
// if it is set to the value greater than zero the result will be greater than the one returned by the model
// if it is smaller than 1 the result will be smaller than the one returned by the model
case class GetNewVM(conservativenessRate: Double)