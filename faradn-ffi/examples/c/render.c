#include <stdio.h>
#include <stdlib.h>
#include "libfaradn.h"

int main(void) {
  graal_isolate_t *isolate = NULL;
  graal_isolatethread_t *thread = NULL;

  if (graal_create_isolate(NULL, &isolate, &thread) != 0) {
    fprintf(stderr, "failed to create isolate\n");
    return 1;
  }

  const char *html = "<h1>Receipt</h1><p>Total: <b>10,00</b></p>";
  char *buffer = NULL;
  long long length = 0;

  int rc = faradn_render(thread, (char *) html, (char *) "tm-t88v", &buffer, &length);
  if (rc != 0) {
    fprintf(stderr, "faradn_render failed: %d\n", rc);
    graal_tear_down_isolate(thread);
    return 1;
  }

  /* ESC/POS bytes: pipe to the printer (e.g. `| nc PRINTER 9100`) or to a file. */
  fwrite(buffer, 1, (size_t) length, stdout);
  faradn_free(thread, buffer);

  graal_tear_down_isolate(thread);
  return 0;
}
