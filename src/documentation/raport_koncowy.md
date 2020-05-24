### Agent lokalny
Celem agenta lokalnego jest obserwowanie zużycia zasobów przez klienta i na ich podstawie przewidywanie ich zużycia w chwili t+1. Aby zasymulować sytuację na której możliwe będzie przetestowanie systemu, agent lokalny generuje wartości funkcji sin od t i próbuje przewidzieć wartość tej funkcji w chwili t+1. Działanie agenta jest niezależne od funkcji symulującej zużycie zasobów systemowych, więc jest on w stanie przewidzieć wartość dowolnej funkcji.

#### Kroki podejmowane przez agenta lokalnego w celu przewidzenia zużycia zasobów:
1. Wczytanie historii zużycia zasobów z poprzednio wykonywanej pracy z pliku.
2. Przekształcenie historii do postaci odpowiedniej dla modelu.
3. Trenowanie modelu.
4. Generowanie kolejnych wartości sin w zależności od czasu. Po wygenerowaniu określonej w konfiguracji agenta ilości wartości, model jest przeuczany na nowo, aby był w stanie dostosowywać się do sytuacji, w których funkcja generująca wartości nie jest okresowa.
5. Po zakończeniu generowania wartości, są one zapisane do pliku, aby można było z nich skorzystać podczas trenowania modelu przy kolejnym uruchomieniu agenta. 

#### Dokładny opis działania agenta lokalnego
*1. Wczytanie historii zużycia zasobów z poprzednio wykonywanej pracy z pliku.*
Historia zużycia zasobów jest przechowywana w pliku txt, w którego jednej linii przechowywana jest informacja na temat ilości zużytych danych w postaci licby zmiennoprzecinkowej oraz chwili czasu w, której wystąpiło dane zdarzenie. Fragment pliku jest widoczny na zdjęciu poniżej.

<p align="center">
  <img src = "./raport_koncowy_zdjecia/history_snapshot.png"/>
   <figcaption>Fragment pliku z historią zużycia zasobów</figcaption>
</p>

*2. Przekształcenie historii do postaci odpowiedniej dla modelu.*
