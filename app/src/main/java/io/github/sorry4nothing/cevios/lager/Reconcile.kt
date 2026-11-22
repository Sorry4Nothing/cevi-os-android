package io.github.sorry4nothing.cevios.lager

import io.github.sorry4nothing.cevios.net.ApiError
import io.github.sorry4nothing.cevios.net.ApiResult

/**
 * Ob eine neu geladene Antwort den gezeigten Stand ersetzt. Eine stille Aktualisierung (Feed, Strom) lässt den alten Stand stehen,
 * wenn nur das Netz fehlt. Sagt der Server aber «kein Zugriff» oder «nicht gefunden», ist die Freigabe zurückgenommen oder die Sache
 * weg: Dann bleibt nichts Altes stehen, die Ansicht zeigt den Fehler.
 */
fun replacesState(result: ApiResult<*>, silent: Boolean, hasCurrent: Boolean): Boolean =
    result is ApiResult.Ok || !silent || !hasCurrent ||
        (result is ApiResult.Failure && (result.error == ApiError.FORBIDDEN || result.error == ApiError.NOT_FOUND))

/** Die Liste ohne die Zeilen, die der Feed als gelöscht meldet; ein Fehler oder eine leere Menge bleibt unverändert. */
fun <T> ApiResult<List<T>>?.withoutIds(ids: Set<String>, idOf: (T) -> String): ApiResult<List<T>>? {
    val ok = this as? ApiResult.Ok ?: return this
    if (ids.isEmpty() || ok.value.none { idOf(it) in ids }) return this
    return ApiResult.Ok(ok.value.filterNot { idOf(it) in ids })
}
