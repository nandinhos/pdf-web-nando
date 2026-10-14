// Configuração do pdf.js. O worker é servido como arquivo estático em
// /public/pdf.worker.min.mjs (copiado de pdfjs-dist no postinstall) para evitar
// que o webpack tente bundlá-lo como não-módulo (quebra o build) e para não
// depender de CDN externo (privacidade — PRD §2.2 O4).
import * as pdfjs from "pdfjs-dist";

if (typeof window !== "undefined") {
  pdfjs.GlobalWorkerOptions.workerSrc = "/pdf.worker.min.mjs";
}

export { pdfjs };
